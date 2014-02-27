package views.helper

import play.api.i18n.Lang

import org.overviewproject.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState}

object DocumentSetHelper {
  /** A CSS class name describing a job's state. */
  def jobStateClass(state: DocumentSetCreationJobState.Value) = {
    state.toString.toLowerCase.replace('_', '-')
  }

  /** An i18n-ized String describing a job that we know is not in the queue.
    *
    * @param jobDescriptionKey A key, like "clustering_level:4"
    * @return A translated string, like "Clustering (4)"
    */
  def jobDescriptionKeyToMessage(jobDescriptionKey: String)(implicit lang: Lang): String = {
    val keyAndArgs : Seq[String] = jobDescriptionKey.split(':')
    val key = keyAndArgs.head
    if (key == "") {
      ""
    } else {
      val m = views.ScopedMessages("views.ImportJob._documentSetCreationJob.job_state_description")
      m(key, keyAndArgs.drop(1) : _*)
    }
  }

  /** An i18n-ized String describing a job.
    *
    * @param job A DocumentSetCreationJob
    * @param nAheadInQueue Number of jobs ahead of this one in the queue
    * @return A translated string, like "Clustering (4)"
    */
  def jobDescriptionMessage(job: DocumentSetCreationJob, nAheadInQueue: Long)(implicit lang: Lang): String = {
    if (nAheadInQueue > 0) {
      views.Magic.t("views.ImportJob._documentSetCreationJob.jobs_to_process", nAheadInQueue)
    } else {
      jobDescriptionKeyToMessage(job.statusDescription)
    }
  }
}
