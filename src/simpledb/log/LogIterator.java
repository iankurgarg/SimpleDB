package simpledb.log;

import static simpledb.file.Page.INT_SIZE;
import simpledb.file.*;
import java.util.Iterator;

/**
 * A class that provides the ability to move through the
 * records of the log file in reverse order.
 * 
 * @author Edward Sciore
 */
//made the class public
public class LogIterator implements Iterator<BasicLogRecord> {
   private Block blk;
   private Page pg = new Page();
   private int currentrec;
   
   //need this variable
   private int LastBlockNumber;
   
   /**
    * Creates an iterator for the records in the log file,
    * positioned after the last log record.
    * This constructor is called exclusively by
    * {@link LogMgr#iterator()}.
    */
   LogIterator(Block blk) {
      this.blk = blk;
      pg.read(blk);
      LastBlockNumber = blk.number();
      currentrec = pg.getInt(LogMgr.LAST_POS);
   }
   
   //constructor for forward scanning. The current rec line changed
   LogIterator(Block blk,boolean fwd) {
	      this.blk = blk;
	      pg.read(blk);
	      currentrec = LogMgr.LAST_POS + INT_SIZE;
   }
   
   /**
    * Determines if the current log record
    * is the earliest record in the log file.
    * @return true if there is an earlier record
    */
   public boolean hasNext() {
      return currentrec>0 || blk.number()>0;
   }
   
   public boolean hasNextForward(){
	   //WE NEED TO STORE THE LAST BLOCK NUMBER TOO.
	   return currentrec < pg.getInt(LogMgr.LAST_POS) || blk.number() < LastBlockNumber;
   }
   
   /**
    * Moves to the next log record in reverse order.
    * If the current log record is the earliest in its block,
    * then the method moves to the next oldest block,
    * and returns the log record from there.
    * @return the next earliest log record
    */
   public BasicLogRecord next() {
      if (currentrec == 0) 
         moveToNextBlock();
      currentrec = pg.getInt(currentrec);
      return new BasicLogRecord(pg, currentrec+INT_SIZE+INT_SIZE);
   }
   
   public BasicLogRecord nextForward(){
	   if (currentrec == pg.getInt(LogMgr.LAST_POS) && blk.number() < LastBlockNumber){
		   moveToNextForwardBlock();
	   }
	   //move current rec forward. then use the last four bytes to return the basic log record. 
	   int thisrec = currentrec;
	   currentrec = pg.getInt(currentrec+INT_SIZE) - INT_SIZE;
	   return new BasicLogRecord(pg, thisrec+INT_SIZE+INT_SIZE);
   }
   
   public void remove() {
      throw new UnsupportedOperationException();
   }
   
   /**
    * Moves to the next log block in reverse order,
    * and positions it after the last record in that block.
    */
   private void moveToNextBlock() {
      blk = new Block(blk.fileName(), blk.number()-1);
      pg.read(blk);
      currentrec = pg.getInt(LogMgr.LAST_POS);
   }
   
   private void moveToNextForwardBlock(){
	   blk = new Block(blk.fileName(), blk.number()+1);
	   pg.read(blk);
	   currentrec = LogMgr.LAST_POS; //this is 0+int size which means the second block
   }
}
