package org.overviewproject.documentcloud

import akka.actor.Actor

import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import scala.concurrent.Promise
import scala.util.control.Exception._


object DocumentReceiverProtocol {
  /** No more documents will be processed */
  case class Done()
}

/**
 * Actor that serializes the processing of documents retrieved from DocumentCloud.
 * Calls the specified callback function with the received document information and text.
 * When the specified number of documents has been received, the finished Promise
 * is completed.
 * If a document retrieval fails because of an exception or other error, the finished
 * promise will fail.
 * 
 * @todo Handle exceptions in callback
 * 
 * @param processDocument The callback function that does the actual processing of the documents.
 * @param numberOfDocuments The number of documents to retrieved
 * @param finished Contains information about any failed document retrieval attempts
 */
class DocumentReceiver(processDocument: (Document, String) => Unit, finished: Promise[Seq[DocumentRetrievalError]]) extends Actor {
  import DocumentReceiverProtocol._
  
  var receivedDocuments: Int = 0 
  var failedRetrievals: Seq[DocumentRetrievalError] = Seq.empty
  
  def receive = {
    case GetTextSucceeded(document, text) => {
      failOnError { processDocument(document, text) }
    }
    case GetTextFailed(url, text, maybeStatus, maybeHeaders) => {
      failedRetrievals = failedRetrievals :+ DocumentRetrievalError(url, text, maybeStatus, maybeHeaders)
    }
    case GetTextError(error) => finished.failure(error)
    case Done() => finished.success(failedRetrievals)
  }
  
  /** 
   *  Ensure that finished is completed. If some external source stops the actor, the Promise still succeeds
   */
  override def postStop = if (!finished.isCompleted) finished.success(failedRetrievals)

  /** Apply to methods that can throw, and complete `finished` with failure if they do */
  private val failOnError = allCatch withApply { error => finished.failure(error) }
  
}