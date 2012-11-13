/*
 * LargeObject.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */
 
package models.upload


import java.sql.SQLException
import org.postgresql.largeobject.{LargeObject => PGLargeObject}
import org.postgresql.largeobject.LargeObject._
import org.postgresql.PGConnection
import scala.util.control.Exception._

/**
 * Interface to a Postgres LargeObject
 */ 
trait LargeObject {
  /** The object id of the LargeObject in the database */
  val oid: Long

  /** Appends data to the end of the LargeObject. @return total size of object */
  def add(data: Array[Byte]): Long

  /** Read the first n bytes. FIXME: for testing only right now. Proper methods later */
  def read(length: Int): Array[Byte]

  /** delete all stored data */
  def truncate
}

/**
 * Helper object providing contexts containing LargeObjects. Ensures that the underlying
 * LargeObject is closed after execution. Data is only guaranteed to have been written to the
 * database after the LargeObject has been closed.
 * Right now it expects an implicit PGConnection - this may change it it's not the
 * correct abstraction.
 */
object LO {
  
  /**
   * Provides a scope with a newly created LargeObject.
   * @return None if an error occurs, and Option[A] (return type of block) on success.
   * Returning an Either might be more fun.
   */
  def withLargeObject[A](block: LargeObject => A)(implicit pgConnection: PGConnection): A = {
    val oid = loManager.createLO

    withLargeObject(oid)(block)
  }

  /**
   * Provides a scope with a LargeObject with the specified oid.
   * @return None if an error occurs, or if there is no object in the database with the oid,
   * and Option[A] on success.
   * Returning an Either might be more fun here too.
   */
  def withLargeObject[A](oid: Long)(block: LargeObject => A)(implicit pgConnection: PGConnection): A = {
    val lo = loManager.open(oid)
    val r = block(new LargeObjectImpl(lo))
    lo.close
    r
  }

  /**
   * Deletes the LargeObject with the specified oid.
   * If no such LargeObject exists, a PSQLException is thrown.
   */
  def delete(oid: Long)(implicit pgConnection: PGConnection) {
    loManager.delete(oid)
  }
  
  private def loManager(implicit pgConnection: PGConnection) = pgConnection.getLargeObjectAPI()

  private class LargeObjectImpl(pgLo: PGLargeObject) extends LargeObject {
    val oid: Long = pgLo.getLongOID

    def add(data: Array[Byte]): Long = {
      pgLo.seek(0, SEEK_END) // Hopefully this is efficient even in the case of multiple writes.
      pgLo.write(data, 0, data.size)
      pgLo.tell
    }

    def read(length: Int): Array[Byte] = {
      pgLo.read(length)
    }

    def truncate { pgLo.truncate(0) }
  }
}
