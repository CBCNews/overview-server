package org.overviewproject.jobhandler.documentset

import akka.actor._
import akka.testkit.{ TestActorRef, TestProbe }
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.jobhandler.documentset.SearchHandlerProtocol.SearchDocumentSet
import org.overviewproject.jobhandler.documentset.SearchIndexSearcherProtocol._
import org.overviewproject.test.{ ActorSystemContext, ForwardingActor }
import org.specs2.mutable.{ Before, Specification }
import org.specs2.specification.Scope

class SearchHandlerSpec extends Specification {

  "SearchHandler" should {

    abstract class SearchHandlerWithStorageReporting(searchExistence: Boolean, storageProbe: ActorRef) extends SearchHandler with SearchHandlerComponents {
      class ReportingStorage extends Storage {
        override def queryForProject(documentSetId: Long, searchTerms: String): String = "search terms"
        override def searchExists(documentSetId: Long, searchTerms: String): Boolean = searchExistence
        override def createSearchResult(documentSetId: Long, searchTerms: String): Long = 1l
        override def completeSearch(searchId: Long, documentSetId: Long, query: String): Unit = {
          storageProbe ! s"completeSearch($searchId, $documentSetId, $query)"
        }
        override def failSearch(searchId: Long, documentSetId: Long, query: String): Unit = {
          storageProbe ! s"failSearch($searchId, $documentSetId, $query)"
        }
      }

      override val storage = new ReportingStorage
    }

    class TestSearchHandler(searchExistence: Boolean, storageProbe: ActorRef) extends SearchHandlerWithStorageReporting(searchExistence, storageProbe) {
      override val actorCreator = new ActorCreator {
        override def produceDocumentSearcher(documentSetId: Long, query: String): Actor =
          new Actor {
            def receive = {
              case _ =>
            }
          }
      }
    }

    class SearchHandlerWithDocumentSearcherProbe(searchExistence: Boolean, storageProbe: ActorRef, documentSearcherProbe: ActorRef) extends SearchHandlerWithStorageReporting(searchExistence, storageProbe) {
      override val actorCreator = new ActorCreator {
        override def produceDocumentSearcher(documentSetId: Long, query: String): Actor =
          new ForwardingActor(documentSearcherProbe)
      }
    }

    // A SearchHandler where the documentSearcher stops when receiving any message
    // simulating a failure
    class SearchHandlerWithSearchFailure(searchExistence: Boolean, storageProbe: ActorRef) extends SearchHandlerWithStorageReporting(searchExistence, storageProbe) {

      override val actorCreator = new ActorCreator {
        override def produceDocumentSearcher(documentSetId: Long, query: String): Actor =
          new Actor {
            def receive = {
              case _ => context.stop(self) // FIXME: throwing exception produces ugly output
            }
          }
      }
    }

    // The SearchHandler communicates with its parent. The SearchHandlerParent test class
    // allows us to monitor that communication
    class SearchHandlerParent(searchHandlerProps: Props, parentProbe: ActorRef) extends Actor {
      val searchHandler = context.actorOf(searchHandlerProps, "SearchHandler")
      context.watch(searchHandler)

      def receive = {
        case Terminated(searchHandler) => context.stop(self)
        case msg if sender == searchHandler => parentProbe forward msg
        case msg => searchHandler forward msg
      }

    }

    // Mixin traits, determining how the mock storage responds to searchExists
    trait SearchExistence {
      def searchExists: Boolean
    }

    trait ExistingSearch extends SearchExistence {
      override def searchExists: Boolean = true
    }

    trait NoExistingSearch extends SearchExistence {
      override def searchExists: Boolean = false
    }

    trait SearchInfo {
      val documentSetId = 5l
      val searchTerms = "search terms"
    }

    abstract class MonitoredSearchHandlerContext extends ActorSystemContext with SearchInfo with Before {
      var searchHandlerParent: ActorRef = _
      var searchHandlerProbe: TestProbe = _
      var storageProbe: TestProbe = _
      var parentProbe: TestProbe = _

      def before: Unit = {
        searchHandlerProbe = TestProbe()
        storageProbe = TestProbe()
        parentProbe = TestProbe()

        searchHandlerParent = system.actorOf(parentProps, "SearchHandlerParent")

        parentProbe watch searchHandlerParent
      }

      protected def searchHandlerProps: Props
      private def parentProps = Props(new SearchHandlerParent(searchHandlerProps, parentProbe.ref))
    }

    // FIXME: SearchHandlerContext and SearchHandlerWithDocumentSearcherContext are the same
    // because we have to make sure we receive StartSearch to avoid warnings.
    abstract class SearchHandlerContext extends MonitoredSearchHandlerContext {
      self: SearchExistence =>
      //override protected def searchHandlerProps = Props(new TestSearchHandler(searchExists, storageProbe.ref))
      lazy val documentSearcherProbe: TestProbe = TestProbe()
      override protected def searchHandlerProps = Props(new SearchHandlerWithDocumentSearcherProbe(searchExists, storageProbe.ref, documentSearcherProbe.ref))
    }

    abstract class SearchHandlerWithDocumentSearcherContext extends MonitoredSearchHandlerContext {
      lazy val documentSearcherProbe: TestProbe = TestProbe()
      override protected def searchHandlerProps = Props(new SearchHandlerWithDocumentSearcherProbe(false, storageProbe.ref, documentSearcherProbe.ref))
    }

    abstract class FailingSearchHandlerContext extends MonitoredSearchHandlerContext {
      override protected def searchHandlerProps = Props(new SearchHandlerWithSearchFailure(false, storageProbe.ref))
    }

    "send JobDone to parent if SearchResult already exists" in new SearchHandlerContext with ExistingSearch {
      searchHandlerParent ! SearchDocumentSet(documentSetId, searchTerms)
      
      parentProbe.expectMsg(JobDone(documentSetId))
      parentProbe.expectTerminated(searchHandlerParent)
    }

    "create a new SearchResult and start Searcher if SearchResult doesn't exist" in new SearchHandlerWithDocumentSearcherContext {
      searchHandlerParent ! SearchDocumentSet(documentSetId, searchTerms)

      documentSearcherProbe.expectMsg(StartSearch(1l, documentSetId, searchTerms))
    }

    "send JobDone to parent when receiving SearchComplete from Searcher" in new SearchHandlerContext with NoExistingSearch {
      searchHandlerParent ! SearchDocumentSet(documentSetId, searchTerms)
      documentSearcherProbe.expectMsgType[StartSearch]

      searchHandlerParent ! SearchComplete

      parentProbe.expectMsg(JobDone(documentSetId))
      parentProbe.expectTerminated(searchHandlerParent)
    }

    "set SearchResult state to Complete when receiving SearchComplete from Searcher" in new SearchHandlerContext with NoExistingSearch {
      searchHandlerParent ! SearchDocumentSet(documentSetId, searchTerms)
      documentSearcherProbe.expectMsgType[StartSearch]

      searchHandlerParent ! SearchComplete

      parentProbe.expectMsg(JobDone(documentSetId))
      parentProbe.expectMsgType[Terminated]
      storageProbe.expectMsg(s"completeSearch(1, $documentSetId, $searchTerms)")
    }

    "set SearchResultState to Error when receiving SearchFailure from Searcher" in new SearchHandlerContext with NoExistingSearch {
      val error = new Exception("exception from RequestQueue")

      searchHandlerParent ! SearchDocumentSet(documentSetId, searchTerms)
      documentSearcherProbe.expectMsgType[StartSearch]

      searchHandlerParent ! SearchFailure(error)

      parentProbe.expectMsg(JobDone(documentSetId))
      parentProbe.expectMsgType[Terminated]
      storageProbe.expectMsg(s"failSearch(1, $documentSetId, $searchTerms)")
    }

    "set SearchResultState to Error if Searcher dies unexpectedly" in new FailingSearchHandlerContext {
      searchHandlerParent ! SearchDocumentSet(1l, "search terms")

      storageProbe.expectMsg("failSearch(1, 1, search terms)")
      parentProbe.expectMsg(JobDone(1l))
      parentProbe.expectTerminated(searchHandlerParent)

    }
  }

}