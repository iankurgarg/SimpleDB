package simpledb.tx.recovery;

import static simpledb.tx.recovery.LogRecord.*;
import simpledb.file.Block;
import simpledb.buffer.Buffer;
import simpledb.server.SimpleDB;
import java.util.*;

/**
 * The recovery manager.  Each transaction has its own recovery manager.
 * @author Edward Sciore
 */
public class RecoveryMgr {
   private int txnum;

   /**
    * Creates a recovery manager for the specified transaction.
    * @param txnum the ID of the specified transaction
    */
   public RecoveryMgr(int txnum) {
      this.txnum = txnum;
      new StartRecord(txnum).writeToLog();
   }

   /**
    * Writes a commit record to the log, and flushes it to disk.
    */
   public void commit() {
      SimpleDB.bufferMgr().flushAll(txnum);
      int lsn = new CommitRecord(txnum).writeToLog();
      SimpleDB.logMgr().flush(lsn);
   }

   /**
    * Writes a rollback record to the log, and flushes it to disk.
    */
   public void rollback() {
      doRollback();
      SimpleDB.bufferMgr().flushAll(txnum);
      int lsn = new RollbackRecord(txnum).writeToLog();
      SimpleDB.logMgr().flush(lsn);
   }

   /**
    * Recovers uncompleted transactions from the log,
    * then writes a quiescent checkpoint record to the log and flushes it.
    */
   public void recover() {
      doRecover();
      SimpleDB.bufferMgr().flushAll(txnum);
      int lsn = new CheckpointRecord().writeToLog();
      SimpleDB.logMgr().flush(lsn);

   }

   /**
    * Writes a setint record to the log, and returns its lsn.
    * Updates to temporary files are not logged; instead, a
    * "dummy" negative lsn is returned.
    * @param buff the buffer containing the page
    * @param offset the offset of the value in the page
    * @param newval the value to be written
    */
   public int setInt(Buffer buff, int offset, int newval) {
      int oldval = buff.getInt(offset);
      Block blk = buff.block();
      if (isTempBlock(blk))
         return -1;
      else
         return new SetIntRecord(txnum, blk, offset, oldval, newval).writeToLog();
   }

   /**
    * Writes a setstring record to the log, and returns its lsn.
    * Updates to temporary files are not logged; instead, a
    * "dummy" negative lsn is returned.
    * @param buff the buffer containing the page
    * @param offset the offset of the value in the page
    * @param newval the value to be written
    */
   public int setString(Buffer buff, int offset, String newval) {
      String oldval = buff.getString(offset);
      Block blk = buff.block();
      if (isTempBlock(blk))
         return -1;
      else
         return new SetStringRecord(txnum, blk, offset, oldval, newval).writeToLog();
   }

   /**
    * Rolls back the transaction.
    * The method iterates through the log records,
    * calling undo() for each log record it finds
    * for the transaction,
    * until it finds the transaction's START record.
    */
   private void doRollback() {
      //Iterator<LogRecord> iter = new LogRecordIterator();
      LogRecordIterator iter = new LogRecordIterator(); //Not sure if this is necessary no syntax error though.
      while (iter.hasNext()) {
         LogRecord rec = iter.next();
         if (rec.txNumber() == txnum) {
            if (rec.op() == START)
               return;
            rec.undo(txnum);
         }
      }
   }

   /**
    * Does a complete database recovery.
    * The method iterates through the log records.
    * Whenever it finds a log record for an unfinished
    * transaction, it calls undo() on that record.
    * The method stops when it encounters a CHECKPOINT record
    * or the end of the log.
    */
   private void doRecover() {
      Collection<Integer> rolledBackTxs = new ArrayList<Integer>();
      Collection<Integer> committedTxs = new ArrayList<Integer>();
      //Iterator<LogRecord> iter = new LogRecordIterator();
    
      /*
       * changed the data type of iter in the below line to support additional operations like
       * hasnextforward and has nextforward which are not supported by the standard iterator.
       */
      LogRecordIterator iter = new LogRecordIterator();
      //undo phase
      while (iter.hasNext()) {
         LogRecord rec = iter.next();
         if (rec.op() == CHECKPOINT)
            break;
         if (rec.op() == COMMIT)
            committedTxs.add(rec.txNumber());
         else if (rec.op() == ROLLBACK)
        	 rolledBackTxs.add(rec.txNumber());
         else if (!committedTxs.contains(rec.txNumber()) && !rolledBackTxs.contains(rec.txNumber()))
         {	
        	 rec.undo(txnum);
         }
      }
      
      //redo phase
      while(iter.hasNextForward()) {
    	  LogRecord rec = iter.nextForward();
    	  if (!(rec.op() == COMMIT || rec.op() == ROLLBACK)) {
    		  if (committedTxs.contains(rec.txNumber()))
    		  {
    			  rec.redo(rec.txNumber());
    		  }
    	  }
      }
      
      
//      //This loop will eventually replace the above while loop after some minor modifications
//      while(iter.actualhasNext())
//      {
//    	  LogRecord rec = iter.actualnext();
//    	  //if record is update record and txnum not in commit list or rollback list
//    	  //rewrite the transaction to the page
//      }
      
      
      //flush the data to disk
      //create checkpoint.
   }

   /**
    * Determines whether a block comes from a temporary file or not.
    */
   private boolean isTempBlock(Block blk) {
      return blk.fileName().startsWith("temp");
   }
}
