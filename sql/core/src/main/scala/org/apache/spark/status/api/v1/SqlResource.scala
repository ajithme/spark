/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.status.api.v1

import java.util.Date
import javax.ws.rs.{DefaultValue, GET, Path, PathParam, Produces, QueryParam}
import javax.ws.rs.core.MediaType

import org.apache.spark.JobExecutionStatus
import org.apache.spark.sql.execution.ui.{SQLAppStatusStore, SQLExecutionUIData, SQLPlanMetric}
import org.apache.spark.ui.UIUtils

@Produces(Array(MediaType.APPLICATION_JSON))
private[v1] class SqlResource extends BaseAppResource {

  @GET
  def sqlList(@QueryParam("details") @DefaultValue("false") details: Boolean):
   Seq[ExecutionData] = {
    withUI { ui =>
      val sqlStore = new SQLAppStatusStore(ui.store.store)
      sqlStore.executionsList().map(prepareExecutionData(_, details))
    }
  }

  @GET
  @Path("{executionId:\\d+}")
  def sql(@PathParam("executionId") execId: Long,
          @QueryParam("details") @DefaultValue("false") details: Boolean): ExecutionData = {
    withUI { ui =>
      val sqlStore = new SQLAppStatusStore(ui.store.store)
      sqlStore
        .execution(execId)
        .map(prepareExecutionData(_, details))
        .getOrElse(throw new NotFoundException("unknown id: " + execId))
    }
  }

  def printableMetrics(metrics: Seq[SQLPlanMetric],
                        metricValues: Map[Long, String]): Seq[(String, String)] = {
    metrics.map(metric => (metric.name, metricValues.get(metric.accumulatorId).getOrElse("")))
  }

  def prepareExecutionData(exec: SQLExecutionUIData, details: Boolean): ExecutionData = {
    var running = Seq[Int]()
    var completed = Seq[Int]()
    var failed = Seq[Int]()

    exec.jobs.foreach {
      case (id, JobExecutionStatus.RUNNING) =>
        running = running :+ id
      case (id, JobExecutionStatus.SUCCEEDED ) =>
        completed = completed :+ id
      case (id, JobExecutionStatus.FAILED) =>
        failed = failed :+ id
      case _ =>
    }

    val status = if (exec.jobs.size == completed.size) {
      "COMPLETED"
    } else if (failed.nonEmpty) {
      "FAILED"
    } else {
      "RUNNING"
    }

    val duration = UIUtils.formatDuration(
      exec.completionTime.getOrElse(new Date()).getTime - exec.submissionTime)
    val planDetails = if (details) exec.physicalPlanDescription else ""
    val metrics = if (details) printableMetrics(exec.metrics, exec.metricValues) else Seq.empty
    new ExecutionData(exec.executionId,
      status, exec.description, planDetails, metrics,
      UIUtils.formatDate(exec.submissionTime),
      duration, running, completed, failed)
  }
}
