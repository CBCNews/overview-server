package views.html.DocumentSet

import org.overviewproject.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState}

class _treeJobSpec extends views.html.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    def job: DocumentSetCreationJob = {
      val j = mock[DocumentSetCreationJob]
      j.documentSetId returns 1
      j.id returns 2
      j.treeTitle returns Some("tree title")
      j
    }

    override def result = _treeJob(job)
  }

  "views.html.DocumentSet._treeErrorJob" should {
    "with progress" should {
      trait InProgressScope extends BaseScope {
        override def job = {
          val job = super.job
          job.state returns DocumentSetCreationJobState.InProgress
          job.statusDescription returns "retrieving_documents:10:20"
          job.fractionComplete returns 0.25
          job
        }
      }

      "have class=in-progress" in new InProgressScope {
        Option($("li").attr("class")) must beSome("in-progress")
      }

      "set data-job-id" in new InProgressScope {
        Option($("li").attr("data-job-id")) must beSome("2")
      }

      "display the status" in new InProgressScope {
        $(".status-description").text() must beEqualTo("Retrieving document 10/20")
      }

      "show a progressbar" in new InProgressScope {
        val $progress = $("progress")
        $progress.length must beEqualTo(1)
        Option($progress.attr("value")) must beSome("0.25")
        Option($progress.attr("max")) must beNone
      }
    }

    "with an error" should {
      trait ErrorScope extends BaseScope {
        override def job = {
          val job = super.job
          job.statusDescription returns "worker_error"
          job.state returns DocumentSetCreationJobState.Error
          job
        }
      }

      "have class=error" in new ErrorScope {
        Option($("li").attr("class")) must beSome("error")
      }

      "set data-job-id" in new ErrorScope {
        Option($("li").attr("data-job-id")) must beSome("2")
      }

      "not show a progress bar" in new ErrorScope {
        $("progress").length must beEqualTo(0)
      }

      "display the title" in new ErrorScope {
        Option($("li h6").text()) must beSome((s: String) => s must contain("tree title"))
      }
    }
  }
}
