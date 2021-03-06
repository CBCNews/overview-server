package controllers

import play.api.i18n.Messages
import play.api.mvc.Controller

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities._
import controllers.forms.TreeCreationJobForm
import models.orm.finders.{DocumentSetFinder, TagFinder, TreeFinder}
import models.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.tree.orm.{DocumentSet, DocumentSetCreationJob, Tag, Tree}

trait TreeController extends Controller {
  trait Storage {
    def findDocumentSet(id: Long) : Option[DocumentSet]
    def findTree(id: Long) : Option[Tree]
    def findTag(documentSetId: Long, tagId: Long) : Option[Tag]

    /** Returns true iff we can search the document set.
      *
      * This is a '''hack'''. All document sets ''should'' be searchable, but
      * document sets imported before indexing was implemented are not.
      */
    def isDocumentSetSearchable(documentSet: DocumentSet): Boolean

    /** Inserts the job into the database and returns that copy */
    def insertJob(job: DocumentSetCreationJob): DocumentSetCreationJob
  }

  val storage : TreeController.Storage

  def show(documentSetId: Long, treeId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)) { implicit request =>
    val stuff = for (tree <- storage.findTree(treeId).filter(_.documentSetId == documentSetId);
                     documentSet <- storage.findDocumentSet(documentSetId)) yield (tree, documentSet)

    stuff match {
      case None => NotFound
      case Some((tree, documentSet)) =>
        val isSearchable = storage.isDocumentSetSearchable(documentSet)
        Ok(views.html.Tree.show(request.user, documentSet, tree, isSearchable))
    }
  }

  private def tagToTreeDescription(tag: Tag) : String = {
    Messages("controllers.TreeController.treeDescription.fromTag", tag.name)
  }

  private def augmentJobWithDescription(job: DocumentSetCreationJob) : Either[String,DocumentSetCreationJob] = {
    job.tagId match {
      case None => Right(job)
      case Some(tagId) => {
        storage.findTag(job.documentSetId, tagId) match {
          case None => Left("tag not found")
          case Some(tag) => Right(job.copy(
            treeDescription=Some(tagToTreeDescription(tag))
          ))
        }
      }
    }
  }

  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val form = TreeCreationJobForm(documentSetId)
    form.bindFromRequest.fold(
      f => BadRequest,
      j => {
        augmentJobWithDescription(j) match {
          case Right(goodJob) =>
            storage.insertJob(goodJob)
            Redirect(routes.DocumentSetController.index()).flashing(
              "event" -> "tree-create"
            )
          case Left(_) =>
            NotFound
        }
      }
    )
  }
}

object TreeController extends TreeController {
  private val FirstSearchableDocumentSetVersion = 2

  object DatabaseStorage extends Storage {
    override def isDocumentSetSearchable(documentSet: DocumentSet) = documentSet.version >= FirstSearchableDocumentSetVersion
    override def findDocumentSet(id: Long) = DocumentSetFinder.byDocumentSet(id).headOption
    override def findTree(id: Long) = TreeFinder.byId(id).headOption
    override def findTag(documentSetId: Long, tagId: Long) = TagFinder.byDocumentSetAndId(documentSetId, tagId).headOption
    override def insertJob(job: DocumentSetCreationJob) = DocumentSetCreationJobStore.insertOrUpdate(job)
  }

  override val storage = DatabaseStorage
}
