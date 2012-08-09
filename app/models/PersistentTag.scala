package models

import java.sql.Connection

trait PersistentTag {
  val id: Long
  
  def count(implicit c: Connection): Long
  def countsPerNode(nodeIds: Seq[Long])(implicit c: Connection) : Seq[(Long, Long)]
  def loadTag(implicit c: Connection) : core.Tag
}

object PersistentTag {
  
  def findOrCreateByName(name: String, documentSetId: Long,
		  				 loader: PersistentTagLoader = new PersistentTagLoader(), 
		  				 saver: PersistentTagSaver = new PersistentTagSaver())
  						(implicit c: Connection) : PersistentTag = {
    val tagId = loader.loadByName(name) match {
      case Some(id) => id
      case None => saver.save(name, documentSetId).get 
    }
    
    new PersistentTagImpl(tagId, name, loader, saver)
  }
  
  def findByName(name: String, documentSetId: Long,
		  	     loader: PersistentTagLoader = new PersistentTagLoader(), 
		  		 saver: PersistentTagSaver = new PersistentTagSaver())
  				(implicit c: Connection) : Option[PersistentTag] = {
	loader.loadByName(name) match {
	  case Some(id) => Some(new PersistentTagImpl(id, name, loader, saver))
	  case None => None
	}
  }
    
  private class PersistentTagImpl(tagId: Long, name: String,
		  						  loader: PersistentTagLoader, saver: PersistentTagSaver) extends PersistentTag {
    val id = tagId
    
    def count(implicit c: Connection): Long = {
      loader.countDocuments(id)
    }
    
    def countsPerNode(nodeIds: Seq[Long])(implicit c: Connection) : Seq[(Long, Long)] = {
      loader.countsPerNode(nodeIds, id)
    }
    
    def loadTag(implicit c: Connection) : core.Tag = {
      val tagData = loader.loadTag(id)
      core.Tag(0, "foo", null)
    }
    
  }
}