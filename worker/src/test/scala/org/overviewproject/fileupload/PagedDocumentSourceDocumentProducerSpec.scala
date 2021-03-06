package org.overviewproject.fileupload

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.overviewproject.util.DocumentConsumer
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn }

class PagedDocumentSourceDocumentProducerSpec extends Specification with Mockito {


  class TestPagedDocumentProducer(cancellationCheck: ProgressAbortFn) extends PagedDocumentSourceDocumentProducer[Int] {
    override protected val consumer = smartMock[DocumentConsumer]
    override protected val progAbort: ProgressAbortFn = cancellationCheck
    override protected val FetchingFraction = 0.25
    override protected val PreparingFraction = 0.25
    
    override protected val totalNumberOfDocuments = 100l
    
    var documentsProcessed: Int = 0
    override def processDocumentSource(document: Int): Unit = documentsProcessed += 1
    
    override def runQueryForPage(pageNumber: Int)(processDocuments: Iterable[Int] => Int): Int = {
      val pages = Seq(
        Seq.fill(5)(1),
        Seq.fill(4)(2),
        Seq.empty
      )
      
      processDocuments(pages(pageNumber - 1))
    }
    
  }

  def neverCancel(p: Progress): Boolean = false
  def cancelImmediately(p: Progress): Boolean = true
  
  "PagedDocumentSourceDocumentProducer" should {
    
    
    "request documents by page" in {
      val pagedDocumentProducer = new TestPagedDocumentProducer(neverCancel)
      
      val numberOfDocumentsProduced = pagedDocumentProducer.produce()
      
      numberOfDocumentsProduced must be equalTo(9)
      numberOfDocumentsProduced must be equalTo(pagedDocumentProducer.documentsProcessed)
    }
    
    "stop processing after cancelation" in {
      val pagedDocumentProducer = new TestPagedDocumentProducer(cancelImmediately)
      
      val numberOfDocumentsProduced = pagedDocumentProducer.produce()
      numberOfDocumentsProduced must be equalTo(0)
      numberOfDocumentsProduced must be equalTo(pagedDocumentProducer.documentsProcessed)
      
    }
  }
}