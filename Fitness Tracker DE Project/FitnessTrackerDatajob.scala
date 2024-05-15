package org.dezyre

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{Dataset, Row, SparkSession}

import scala.io.Source

object FitnessTrackerDatajob {
  def main(args: Array[String]) = {
    println("Analytics of Fitness Tracker Data")

    val sparkSession: SparkSession = getSparkSession()
    val rawFitnessData: Dataset[Row] = scanRawFitnessData(sparkSession)
    rawFitnessData.show( truncate = false)

    val activityFormattedData: Dataset[Row] = formatActivityColumn(rawFitnessData)
    activityFormattedData.show(false)

    activityFormattedData.printSchema()

    val timeStampFormattedData: Dataset[Row] = formatTheTimeStampColumn(activityFormattedData)
     timeStampFormattedData.show(false)

    timeStampFormattedData.printSchema()

    val formattedFitnessData: Dataset[Row] = timeStampFormattedData
    formattedFitnessData.show(false)

    formattedFitnessData.cache()

    val highestToLowestUsersCalorieBurnt: Dataset[Row] =
      caloriesBurntBestToWorst(formattedFitnessData)

    highestToLowestUsersCalorieBurnt.show(false)

    val famalesBestToWorstActivity: Dataset[Row] =
      activityUsedBestToWorstAmongFemales(formattedFitnessData)

    famalesBestToWorstActivity.show(false)

    val famalesBestToWorstActivity2: Dataset[Row] =
      activityUsedBestToWorstAmongFemalesSQLStyle(sparkSession, formattedFitnessData)

    famalesBestToWorstActivity2.show(false)

    Thread.sleep(1000000)
    sparkSession.stop()
  }

  def viewData(dataset: Dataset[Row]) = {
    dataset.show( truncate = false)
  }

  def getSparkSession(): SparkSession = {
    SparkSession.
      builder().
      appName("Testing Spark Application").
      config("spark.sql.legacy.timeParserPolicy", "LEGACY").
      master(master = "local").getOrCreate();

  }

  def scanRawFitnessData(sparkSession: SparkSession): Dataset[Row] = {
    sparkSession.
      read.format("csv").
      option("header", true).
      option("inferSchema", true).
      load(System.getenv("path")+ "/Fitness_tracker_data.csv")
  }

  def formatActivityColumn(dataset: Dataset[Row]): Dataset[Row] = {

    dataset.withColumnRenamed("activity",
      "activity_raw").
      withColumn("activity",
        regexp_replace(col("activity_raw"), "_", "")).
      select(dataset.columns.head,
        dataset.columns.tail: _*)
  }

  def formatTheTimeStampColumn(dataset: Dataset[Row]): Dataset[Row] = {
    dataset.withColumnRenamed("time_stamp", "time_stamp_raw").
      withColumn("time_stamp",
        to_timestamp(col("time_stamp_raw"), "dd:MM:yy HH:mm")).
      select(dataset.columns.head, dataset.columns.tail: _*)
  }

  def caloriesBurntBestToWorst(dataset: Dataset[Row]): Dataset[Row] = {
    dataset.groupBy("user_id").
      agg(sum("calories").as("calories")).
      orderBy(col("calories").desc)
  }

  def activityUsedBestToWorstAmongFemales(dataset: Dataset[Row]): Dataset[Row] = {
    dataset.filter("gender == 'F' ").
      groupBy("activity").
      agg(approx_count_distinct(col("user_id")).as("count_of_users")).
      orderBy(col("count_of_users").desc)


  }

  def activityUsedBestToWorstAmongFemalesSQLStyle(sparkSession: SparkSession, dataset: Dataset[Row]): Dataset[Row] = {
    dataset.createOrReplaceTempView("data")

    sparkSession.sql(
      """select
        |activity,
        |count(distinct user_id) as count_of_users
        |from data where gender = 'F'
        |group by activity
        |order by count_of_users
        |desc
        |""".stripMargin)
  }


}
