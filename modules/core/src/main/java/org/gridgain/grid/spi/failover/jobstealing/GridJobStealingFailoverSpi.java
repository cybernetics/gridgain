/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.spi.failover.jobstealing;

import org.gridgain.grid.*;
import org.gridgain.grid.compute.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.resources.*;
import org.gridgain.grid.spi.*;
import org.gridgain.grid.spi.collision.jobstealing.*;
import org.gridgain.grid.spi.failover.*;
import org.gridgain.grid.spi.failover.always.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;

import java.util.*;

import static org.gridgain.grid.spi.collision.jobstealing.GridJobStealingCollisionSpi.*;

/**
 * Job stealing failover SPI needs to always be used in conjunction with
 * {@link GridJobStealingCollisionSpi} SPI. When {@link GridJobStealingCollisionSpi}
 * receives a <b>steal</b> request and rejects jobs so they can be routed to the
 * appropriate node, it is the responsibility of this {@code GridJobStealingFailoverSpi}
 * SPI to make sure that the job is indeed re-routed to the node that has sent the initial
 * request to <b>steal</b> it.
 * <p>
 * {@code GridJobStealingFailoverSpi} knows where to route a job based on the
 * {@link GridJobStealingCollisionSpi#THIEF_NODE_ATTR} job context attribute (see {@link GridComputeJobContext}).
 * Prior to rejecting a job,  {@link GridJobStealingCollisionSpi} will populate this
 * attribute with the ID of the node that wants to <b>steal</b> this job.
 * Then {@code GridJobStealingFailoverSpi} will read the value of this attribute and
 * route the job to the node specified.
 * <p>
 * If failure is caused by a node crash, and not by <b>steal</b> request, then this
 * SPI behaves identically to {@link GridAlwaysFailoverSpi}, and tries to find the
 * next balanced node to fail-over a job to.
 * <p>
 * <h1 class="header">Configuration</h1>
 * <h2 class="header">Mandatory</h2>
 * This SPI has no mandatory configuration parameters.
 * <h2 class="header">Optional</h2>
 * This SPI has following optional configuration parameters:
 * <ul>
 * <li>Maximum failover attempts for a single job (see {@link #setMaximumFailoverAttempts(int)}).</li>
 * </ul>
 * Here is a Java example on how to configure grid with {@code GridJobStealingFailoverSpi}.
 * <pre name="code" class="java">
 * GridJobStealingFailoverSpi spi = new GridJobStealingFailoverSpi();
 *
 * // Override maximum failover attempts.
 * spi.setMaximumFailoverAttempts(5);
 *
 * GridConfiguration cfg = new GridConfiguration();
 *
 * // Override default failover SPI.
 * cfg.setFailoverSpiSpi(spi);
 *
 * // Starts grid.
 * G.start(cfg);
 </pre>
 * Here is an example of how to configure {@code GridJobStealingFailoverSpi} from Spring XML configuration file.
 * <pre name="code" class="xml">
 * &lt;property name="failoverSpi"&gt;
 *     &lt;bean class="org.gridgain.grid.spi.failover.jobstealing.GridJobStealingFailoverSpi"&gt;
 *         &lt;property name="maximumFailoverAttempts" value="5"/&gt;
 *     &lt;/bean&gt;
 * &lt;/property&gt;
 * </pre>
 * <p>
 * <img src="http://www.gridgain.com/images/spring-small.png">
 * <br>
 * For information about Spring framework visit <a href="http://www.springframework.org/">www.springframework.org</a>
 * @see GridFailoverSpi
 */
@GridSpiMultipleInstancesSupport(true)
@GridSpiConsistencyChecked(optional = true)
public class GridJobStealingFailoverSpi extends GridSpiAdapter implements GridFailoverSpi,
    GridJobStealingFailoverSpiMBean {
    /** Maximum number of attempts to execute a failed job on another node (default is {@code 5}). */
    public static final int DFLT_MAX_FAILOVER_ATTEMPTS = 5;

    /**
     * Name of job context attribute containing all nodes a job failed on. Note
     * that this list does not include nodes that a job was stolen from.
     *
     * @see GridComputeJobContext
     */
    static final String FAILED_NODE_LIST_ATTR = "gg:failover:failednodelist";

    /**
     * Name of job context attribute containing current failover attempt count.
     * This count is incremented every time the same job gets failed over to
     * another node for execution if it was not successfully stolen.
     *
     * @see GridComputeJobContext
     */
    static final String FAILOVER_ATTEMPT_COUNT_ATTR = "gg:failover:attemptcount";

    /** Maximum failover attempts job context attribute name. */
    private static final String MAX_FAILOVER_ATTEMPT_ATTR = "gg:failover:maxattempts";

    /** Injected grid logger. */
    @GridLoggerResource
    private GridLogger log;

    /** Maximum number of attempts to execute a failed job on another node. */
    private int maxFailoverAttempts = DFLT_MAX_FAILOVER_ATTEMPTS;

    /** Number of jobs that were failed over. */
    private int totalFailedOverJobs;

    /** Number of jobs that were stolen. */
    private int totalStolenJobs;

    /** {@inheritDoc} */
    @Override public int getMaximumFailoverAttempts() {
        return maxFailoverAttempts;
    }

    /**
     * Sets maximum number of attempts to execute a failed job on another node.
     * If job gets stolen and thief node exists then it is not considered as
     * failed job.
     * If not specified, {@link #DFLT_MAX_FAILOVER_ATTEMPTS} value will be used.
     * <p>
     * Note this value must be identical for all grid nodes in the grid.
     *
     * @param maxFailoverAttempts Maximum number of attempts to execute a failed
     *      job on another node.
     */
    @GridSpiConfiguration(optional = true)
    public void setMaximumFailoverAttempts(int maxFailoverAttempts) {
        this.maxFailoverAttempts = maxFailoverAttempts;
    }

    /** {@inheritDoc} */
    @Override public int getTotalFailedOverJobsCount() {
        return totalFailedOverJobs;
    }

    /** {@inheritDoc} */
    @Override public int getTotalStolenJobsCount() {
        return totalStolenJobs;
    }

    /** {@inheritDoc} */
    @Override public Map<String, Object> getNodeAttributes() throws GridSpiException {
        return F.<String, Object>asMap(createSpiAttributeName(MAX_FAILOVER_ATTEMPT_ATTR), maxFailoverAttempts);
    }

    /** {@inheritDoc} */
    @Override public void spiStart(String gridName) throws GridSpiException {
        // Start SPI start stopwatch.
        startStopwatch();

        assertParameter(maxFailoverAttempts >= 0, "maximumFailoverAttempts >= 0");

        if (log.isDebugEnabled())
            log.debug(configInfo("maxFailoverAttempts", maxFailoverAttempts));

        registerMBean(gridName, this, GridJobStealingFailoverSpiMBean.class);

        // Ack ok start.
        if (log.isDebugEnabled())
            log.debug(startInfo());
    }

    /** {@inheritDoc} */
    @Override public void spiStop() throws GridSpiException {
        unregisterMBean();

        // Ack ok stop.
        if (log.isDebugEnabled())
            log.debug(stopInfo());
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override public GridNode failover(GridFailoverContext ctx, List<GridNode> top) {
        assert ctx != null;
        assert top != null;

        if (top.isEmpty()) {
            U.warn(log, "Received empty subgrid and is forced to fail.");

            // Nowhere to failover to.
            return null;
        }

        Integer failoverCnt = ctx.getJobResult().getJobContext().getAttribute(FAILOVER_ATTEMPT_COUNT_ATTR);

        if (failoverCnt == null)
            failoverCnt = 0;

        if (failoverCnt > maxFailoverAttempts) {
            U.error(log, "Failover count exceeded maximum failover attempts parameter [failedJob=" +
                ctx.getJobResult().getJob() + ", maxFailoverAttempts=" + maxFailoverAttempts + ']');

            return null;
        }

        if (failoverCnt == maxFailoverAttempts) {
            U.warn(log, "Job failover failed because number of maximum failover attempts is exceeded [failedJob=" +
                ctx.getJobResult().getJob() + ", maxFailoverAttempts=" + maxFailoverAttempts + ']');

            return null;
        }

        try {
            GridNode thief = null;
            boolean isNodeFailed = false;

            UUID thiefId = ctx.getJobResult().getJobContext().getAttribute(THIEF_NODE_ATTR);

            if (thiefId != null) {
                // Clear attribute.
                ctx.getJobResult().getJobContext().setAttribute(THIEF_NODE_ATTR, null);

                thief = getSpiContext().node(thiefId);

                if (thief != null) {
                    // If sender != receiver.
                    if (thief.equals(ctx.getJobResult().getNode())) {
                        U.error(log, "Job stealer node is equal to job node (will fail-over using " +
                            "load-balancing): " + thief.id());

                        isNodeFailed = true;

                        thief = null;
                    }
                    else if (!top.contains(thief)) {
                        U.warn(log, "Thief node is not part of task topology  (will fail-over using load-balancing) " +
                            "[thief=" + thiefId + ", topSize=" + top.size() + ']');

                        thief = null;
                    }

                    if (log.isDebugEnabled())
                        log.debug("Failing-over stolen job [from=" + ctx.getJobResult().getNode() + ", to=" +
                            thief + ']');
                }
                else {
                    isNodeFailed = true;

                    U.warn(log, "Thief node left grid (will fail-over using load balancing): " + thiefId);
                }
            }
            else
                isNodeFailed = true;

            // If job was not stolen or stolen node is not part of topology,
            // then failover the regular way.
            if (thief == null) {
                Collection<UUID> failedNodes = ctx.getJobResult().getJobContext().getAttribute(FAILED_NODE_LIST_ATTR);

                if (failedNodes == null)
                    failedNodes = new HashSet<>(1);

                if (isNodeFailed)
                    failedNodes.add(ctx.getJobResult().getNode().id());

                // Set updated failed node set into job context.
                ctx.getJobResult().getJobContext().setAttribute(FAILED_NODE_LIST_ATTR, failedNodes);

                // Copy.
                List<GridNode> newTop = new ArrayList<>(top.size());

                for (GridNode n : top) {
                    // Add non-failed nodes to topology.
                    if (!failedNodes.contains(n.id()))
                        newTop.add(n);
                }

                if (newTop.isEmpty()) {
                    U.warn(log, "Received topology with only nodes that job had failed on (forced to fail) " +
                        "[failedNodes=" + failedNodes + ']');

                    // Nowhere to failover to.
                    return null;
                }

                thief = ctx.getBalancedNode(newTop);

                if (thief == null)
                    U.warn(log, "Load balancer returned null node for topology: " + newTop);
            }

            if (isNodeFailed)
                // This is a failover, not stealing.
                failoverCnt++;

            // Even if it was stealing and thief node left grid we assume
            // that it is failover because of the fail.
            ctx.getJobResult().getJobContext().setAttribute(FAILOVER_ATTEMPT_COUNT_ATTR, failoverCnt);

            if (thief != null) {
                totalFailedOverJobs++;

                if (isNodeFailed) {
                    U.warn(log, "Failed over job to a new node [newNode=" + thief.id() +
                        ", oldNode=" + ctx.getJobResult().getNode().id() +
                        ", sesId=" + ctx.getTaskSession().getId() +
                        ", job=" + ctx.getJobResult().getJob() +
                        ", jobCtx=" + ctx.getJobResult().getJobContext() +
                        ", task=" + ctx.getTaskSession().getTaskName() + ']');
                }
                else {
                    totalStolenJobs++;
                    if (log.isInfoEnabled())
                        log.info("Stealing job to a new node [newNode=" + thief.id() +
                            ", oldNode=" + ctx.getJobResult().getNode().id() +
                            ", sesId=" + ctx.getTaskSession().getId() +
                            ", job=" + ctx.getJobResult().getJob() +
                            ", jobCtx=" + ctx.getJobResult().getJobContext() +
                            ", task=" + ctx.getTaskSession().getTaskName() + ']');
                }
            }

            return thief;
        }
        catch (GridException e) {
            U.error(log, "Failed to get next balanced node for failover: " + ctx, e);

            return null;
        }
    }

    /** {@inheritDoc} */
    @Override protected List<String> getConsistentAttributeNames() {
        return Collections.singletonList(createSpiAttributeName(MAX_FAILOVER_ATTEMPT_ATTR));
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridJobStealingFailoverSpi.class, this);
    }
}
