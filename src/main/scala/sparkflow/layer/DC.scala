package sparkflow.layer

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag
import sparkflow._

/**
  * DistributedCollection, analogous to RDD
  */
abstract class DC[T: ClassTag](deps: Seq[Dependency[_]]) extends Dependency[T] {

  private var rdd: RDD[T] = _
  private var checkpointed = false

  protected def computeRDD(sc: SparkContext): RDD[T]

  def getRDD(sc: SparkContext): RDD[T] = {
    if(rdd == null){
      if (checkpointed){
        loadCheckpoint[T](getHash, sc) match {
          case Some(existingRdd) => this.rdd = existingRdd
          case None =>
            this.rdd = computeRDD(sc)
            rdd.cache()
            saveCheckpoint(getHash, rdd)
        }
      } else {
        this.rdd = this.computeRDD(sc)
      }
    }
    rdd
  }

  def map[U: ClassTag](f: T => U): DC[U] = {
    new RDDTransformDC(this, (rdd: RDD[T]) => rdd.map(f), f)
  }

  def filter(f: T => Boolean): DC[T] = {
    new RDDTransformDC(this, (rdd: RDD[T]) => rdd.filter(f), f)
  }

  def flatMap[U: ClassTag](f: T => TraversableOnce[U]): DC[U] = {
    new RDDTransformDC(this, (rdd: RDD[T]) => rdd.flatMap(f), f)
  }

  def zipWithUniqueId(): DC[(T, Long)] = {
    new RDDTransformDC(this, (rdd: RDD[T]) => rdd.zipWithUniqueId, "zipWithUniqueId")
  }

  def mapToResult[U:ClassTag](f: RDD[T] => U): DR[T,U] ={
    new DR[T,U](this, f)
  }

  def mapWith[U:ClassTag, V:ClassTag](dr: DR[_,U])(f: (T,U) => V) = {
    new ResultDepDC(this, dr, f)
  }

  def checkpoint(): this.type = {
    this.checkpointed = true
    this
  }

}
