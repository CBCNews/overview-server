package controllers

import play.api.mvc.Controller
import controllers.auth.{ AuthorizedAction, Authorities }
import controllers.forms.{ DocumentSetForm, DocumentSetUpdateForm }
import controllers.util.JobQueueSender
import models.orm.finders.{ DocumentSetCreationJobFinder, DocumentSetFinder, TreeFinder }
import models.orm.stores.DocumentSetStore
import org.overviewproject.jobs.models.{ CancelUploadWithDocumentSet, Delete }
import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob => Job, Tree }
import org.overviewproject.tree.orm.finders.ResultPage
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.jobs.models.CancelUploadWithDocumentSet

trait DocumentSetController extends Controller {
  import Authorities._

  trait Storage {
    /** Returns a DocumentSet from an ID */
    def findDocumentSet(id: Long): Option[DocumentSet]

    /** Returns all Trees in the given DocumentSets */
    def findTreesByDocumentSets(documentSetIds: Iterable[Long]): Iterable[Tree]

    /** Returns all DocumentSetCreationJobs of failed tree-clustering jobs */
    def findTreeJobsByDocumentSets(documentSetIds: Iterable[Long]): Iterable[Job]

    /** Returns a page of DocumentSets */
    def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DocumentSet]

    /** Returns all active DocumentSetCreationJobs (job, documentSet, queuePosition) */
    def findDocumentSetCreationJobs(userEmail: String): Iterable[(Job, DocumentSet, Long)]

    /** Returns type of the job running for the document set, if any exist */
    def findRunningJobType(documentSetId: Long): Option[DocumentSetCreationJobType.Value]

    def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet

    def deleteDocumentSet(documentSet: DocumentSet): Unit

    def cancelJob(documentSet: DocumentSet): Unit
  }

  /** Merges trees and jobs for one docset into one big Seq */
  private def bundle(jobs: Seq[Job], trees: Seq[Tree]) : Seq[Either[Job,Tree]] = {
    jobs.map(Left(_)) ++ trees.sortBy(- _.createdAt.getTime).map(Right(_))
  }

  private val form = DocumentSetForm()
  protected val indexPageSize = 10

  def index(page: Int) = AuthorizedAction(anyUser) { implicit request =>
    val realPage = if (page <= 0) 1 else page
    val documentSetsPage = storage.findDocumentSets(request.user.email, indexPageSize, realPage)
    val documentSets = documentSetsPage.items.toSeq // Squeryl only lets you iterate once
    val documentSetIds = documentSets.map(_.id)

    val trees = storage
      .findTreesByDocumentSets(documentSetIds)
      .toSeq
      .groupBy(_.documentSetId)

    val treeJobs = storage
      .findTreeJobsByDocumentSets(documentSetIds)
      .toSeq
      .groupBy(_.documentSetId)

    val documentSetsWithTrees : Seq[(DocumentSet, Seq[Either[Job,Tree]])] = documentSets.map { ds: DocumentSet =>
      (ds, bundle(treeJobs.getOrElse(ds.id, Seq()), trees.getOrElse(ds.id, Seq())))
    }

    val resultPage = ResultPage(documentSetsWithTrees, documentSetsPage.pageDetails)

    val jobs = storage.findDocumentSetCreationJobs(request.user.email)

    Ok(views.html.DocumentSet.index(request.user, resultPage, jobs, form))
  }

  def showJson(id: Long) = AuthorizedAction(userViewingDocumentSet(id)) { implicit request =>
    storage.findDocumentSet(id) match {
      case None => NotFound
      case Some(documentSet) => {
        val trees = storage.findTreesByDocumentSets(Seq(id)).toSeq
        val jobs = storage.findTreeJobsByDocumentSets(Seq(id)).toSeq
        Ok(views.json.DocumentSet.show(request.user, documentSet, bundle(jobs, trees)))
      }
    }
  }

  def delete(id: Long) = AuthorizedAction(userOwningDocumentSet(id)) { implicit request =>
    val m = views.Magic.scopedMessages("controllers.DocumentSetController")

    // FIXME: Move all deletion to worker and remove database access here
    // FIXME: Make client distinguish between deleting document sets and canceling jobs

    val documentSet = storage.findDocumentSet(id)
    def onDocumentSet(f: DocumentSet => Unit): Unit =
      documentSet.map(f)

    def done(message: String, event: String) = Redirect(routes.DocumentSetController.index()).flashing(
      "success" -> m(message),
      "event" -> event
    )

    storage.findRunningJobType(id) match {
      case Some(DocumentSetCreationJobType.Recluster) =>
        onDocumentSet(storage.cancelJob)
        done("deleteTree.success", "tree-delete")
      case Some(jobType) =>
        onDocumentSet(storage.cancelJob)
        onDocumentSet(storage.deleteDocumentSet)

        if (jobType == DocumentSetCreationJobType.FileUpload) JobQueueSender.send(CancelUploadWithDocumentSet(id))
        JobQueueSender.send(Delete(id))
        done("deleteJob.success", "document-set-delete")
      case None =>
        onDocumentSet(storage.deleteDocumentSet)
        JobQueueSender.send(Delete(id))
        done("deleteDocumentSet.success", "document-set-delete")
    }
  }

  def update(id: Long) = AuthorizedAction(adminUser) { implicit request =>
    storage.findDocumentSet(id).map { documentSet =>
      DocumentSetUpdateForm(documentSet).bindFromRequest().fold(
        f => BadRequest, { updatedDocumentSet =>
          storage.insertOrUpdateDocumentSet(updatedDocumentSet)
          Ok
        })
    }.getOrElse(NotFound)
  }

  val storage: DocumentSetController.Storage
}

object DocumentSetController extends DocumentSetController {
  object DatabaseStorage extends Storage {
    override def findDocumentSet(id: Long) = DocumentSetFinder.byDocumentSet(id).headOption
    override def findTreesByDocumentSets(documentSetIds: Iterable[Long]) = TreeFinder.byDocumentSets(documentSetIds)

    override def findTreeJobsByDocumentSets(documentSetIds: Iterable[Long]) = {
      DocumentSetCreationJobFinder
        .byDocumentSets(documentSetIds)
        .byJobType(DocumentSetCreationJobType.Recluster)
    }

    override def findDocumentSets(userEmail: String, pageSize: Int, page: Int): ResultPage[DocumentSet] = {
      val query = DocumentSetFinder.byOwner(userEmail)
      ResultPage(query, pageSize, page)
    }

    override def findDocumentSetCreationJobs(userEmail: String): Iterable[(Job, DocumentSet, Long)] = {
      DocumentSetCreationJobFinder
        .byUser(userEmail)
        .excludeTreeCreationJobs
        .withDocumentSetsAndQueuePositions
        .toSeq
    }

    override def insertOrUpdateDocumentSet(documentSet: DocumentSet): DocumentSet = {
      DocumentSetStore.insertOrUpdate(documentSet)
    }

    override def deleteDocumentSet(documentSet: DocumentSet): Unit =
      DocumentSetStore.markDeleted(documentSet)

    override def cancelJob(documentSet: DocumentSet): Unit =
      DocumentSetStore.deleteOrCancelJob(documentSet)

    override def findRunningJobType(documentSetId: Long) =
      DocumentSetCreationJobFinder.byDocumentSet(documentSetId).headOption.map(_.jobType)

  }

  override val storage = DatabaseStorage
}
