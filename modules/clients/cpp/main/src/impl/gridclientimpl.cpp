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
#include "gridgain/impl/utils/gridclientdebug.hpp"

#include <algorithm>

#include <boost/uuid/uuid.hpp>
#include <boost/uuid/uuid_generators.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/uuid/uuid_io.hpp>

#include "gridgain/impl/gridclientimpl.hpp"
#include "gridgain/impl/gridclientdataprojection.hpp"
#include "gridgain/impl/gridclientcomputeprojection.hpp"
#include "gridgain/impl/cmd/gridclientcommandexecutor.hpp"
#include "gridgain/impl/marshaller/gridnodemarshallerhelper.hpp"
#include "gridgain/gridclientexception.hpp"
#include "gridgain/gridclienttopologylistener.hpp"
#include "gridgain/gridclientnode.hpp"

#include "gridgain/impl/utils/gridclientlog.hpp"

/**
 * Public constructor.
 */
GridClientImpl::GridClientImpl(const GridClientConfiguration& cfg,
        std::shared_ptr<GridClientCommandExecutorPrivate>& exec)
        : sharedData(new GridClientSharedData(cfg.protocolConfiguration().uuid(), cfg, exec))
           ,topRefresher(new GridClientTopologyRefresher(cfg.topologyRefreshFrequency(), *this))
           ,threadPool(new GridThreadPool(cfg.threadPoolSize())) {
    // Check configuration sanity
    if (!cfg.servers().empty() && !cfg.routers().empty())
        throw GridClientException("Both servers and routers are specified in configuration, which is not allowed.");

    TGridClientTopologyListenerList topLsnrs = cfg.topologyListeners();

    for (auto i = topLsnrs.begin(); i != topLsnrs.end(); i++)
        addTopologyListener(*i);

    // Refresh the topology based on new data.
    refreshTopology();
}

/**
 * Gets a unique client identifier. This identifier is generated by factory on client creation
 * and used in identification and authentication procedure on server node.
 *
 * @return Generated client id.
 */
GridClientUuid GridClientImpl::id() const {
    return getSharedData()->clientUuid();
}

/**
 * Gets a data projection for a default grid cache with {@code null} name.
 *
 * @return Data projection for grid cache with {@code null} name.
 * @throw GridClientException If client was closed.
 */
TGridClientDataPtr GridClientImpl::data() {
    return getOrCreateDataProjection("");
}

/**
 * Gets a data projection for grid cache with name <tt>cacheName</tt>. If
 * no data configuration with given name was provided at client startup, an
 * exception will be thrown.
 *
 * @param cacheName Grid cache name for which data projection should be obtained.
 * @return Data projection for grid cache with name <tt>cacheName</tt>.
 * @throw GridClientException If client was closed or no configuration with given name was provided.
 */
TGridClientDataPtr GridClientImpl::data(const std::string& cacheName) {
    return getOrCreateDataProjection(cacheName);
}

/**
 * Gets a default compute projection. Default compute projection will include all nodes
 * in remote grid. Selection of node that will be connected to perform operations will be
 * done according to {@link GridClientLoadBalancer} provided in client configuration or
 * according to affinity if projection call involves affinity key.
 * <p>
 * More restricted projection configurations may be created with {@link GridClientCompute} methods.
 *
 * @return Default compute projection.
 *
 * @see GridClientCompute
 */
TGridClientComputePtr GridClientImpl::compute() {
    if (mainComputePrj.get() == NULL) {
        TGridClientSharedDataPtr sd = getSharedData();

        GridClientComputeProjectionImpl* p = new GridClientComputeProjectionImpl(sd, *this, TGridClientNodePredicatePtr(),
                TGridClientLoadBalancerPtr(), threadPool);

        mainComputePrj.reset(p);
    }

    return mainComputePrj;
}

/**
 * Adds topology listener. Remote grid topology is refreshed every
 * {@link GridClientConfiguration#getTopologyRefreshFrequency()} milliseconds. If any node was added or removed,
 * a listener will be notified.
 *
 * @param lsnr Listener to add.
 */
void GridClientImpl::addTopologyListener(TGridClientTopologyListenerPtr lsnr) {
    boost::lock_guard<boost::mutex> g(topLsnrsMux);

    topLsnrs.push_back(lsnr);
}

/**
 * Removes previously added topology listener.
 *
 * @param lsnr Listener to remove.
 */
void GridClientImpl::removeTopologyListener(TGridClientTopologyListenerPtr lsnr) {
    boost::lock_guard<boost::mutex> g(topLsnrsMux);

    topLsnrs.erase(std::find(topLsnrs.begin(), topLsnrs.end(), lsnr));
}

/**
 * Gets an unmodifiable snapshot of topology listeners list.
 *
 * @return List of topology listeners.
 */
TGridClientTopologyListenerListPtr GridClientImpl::topologyListeners() const {
    boost::lock_guard<boost::mutex> g(topLsnrsMux);

    TGridClientTopologyListenerList *l = new TGridClientTopologyListenerList(topLsnrs);

    return TGridClientTopologyListenerListPtr(l);
}

/**
 * This method creates the data-projection if an user has not created
 * the projection by the particular cache name.
 * @param cacheName std::string - The name of the particular cache.
 */
TGridClientDataPtr GridClientImpl::getOrCreateDataProjection(std::string cacheName) {
    TGridClientDataPtr clientData;

    TDataPrjMap::const_iterator itPrj = mainDataPrjs.find(cacheName);

    if (itPrj != mainDataPrjs.end())
        clientData = itPrj->second;
    else {
        std::shared_ptr<GridClientDataProjectionImpl> prjImpl =
            std::shared_ptr<GridClientDataProjectionImpl>(
                new GridClientDataProjectionImpl(
                    getSharedData(),
                    *this,
                    cacheName,
                    TGridClientNodePredicatePtr((TGridClientNodePredicate*)NULL),
                    threadPool,
                    std::set<GridClientCacheFlag>()));
        clientData = prjImpl;

        mainDataPrjs.insert(std::make_pair(cacheName, prjImpl));
    }

    return clientData;
}

void GridClientImpl::stop(bool pWait) {
    topRefresher.reset(); // Destroy the topology refersher and thus stop the refresher thread.

    //invalidate all client data
    for(auto i = mainDataPrjs.begin(); i != mainDataPrjs.end(); i++)
        i->second->invalidate();

    // Invalidate compute projection.
    if (mainComputePrj.get() != NULL)
        mainComputePrj->invalidate();

    if (!pWait)
        sharedData->executor()->stop();

    // Shut down the thread pool.
    GG_LOG_DEBUG0("Stopping the thread pool.");
    threadPool->shutdown();
}

void GridClientImpl::onNodeIoFailed(const GridClientNode& n) {
    GG_LOG_DEBUG("Node IO failed: %s", n.getNodeId().uuid().c_str());

    refreshTopology();
}

void GridClientImpl::refreshTopology() {
    const GridClientConfiguration& clientCfg = sharedData->clientConfiguration();
    TGridClientSocketAddressList addrs = clientCfg.routers().size() > 0
            ? clientCfg.routers()
            : clientCfg.servers();

    if (addrs.empty()) {
        GG_LOG_DEBUG0("Skipping topology refresh (address list is empty).");

        return;
    }

    TGridClientCommandExecutorPtr exec = sharedData->executor();
    bool updated = false;

    GG_LOG_DEBUG0("Started refreshing the topology.");

    GridClientException last;

    for (auto it = addrs.begin(); !updated && it != addrs.end(); ++it) {
        try {
            GG_LOG_DEBUG("Refresh address: %s", it->host().c_str());

            GridTopologyRequestCommand topRqst;
            GridClientMessageTopologyResult rslt;
            TNodesSet nodes;

            // Fills the command by the default value.
            topRqst.setIncludeAttributes(false);
            topRqst.setIncludeMetrics(false);
            topRqst.setClientId(id().uuid());

            topRqst.setRequestId(topRqst.generateNewId());

            // Executes the topology command.
            exec->executeTopologyCmd(*it, topRqst, rslt);

            TNodesList nbns = rslt.getNodes();

            // Extract the actual list of nodes.
            nodes.insert(nbns.begin(), nbns.end());

            TNodesSet prevNodes = sharedData->topology()->nodes();

            // Update the topology.
            sharedData->topology()->update(nodes);

            fireTopologyEvents(nodes, prevNodes);

            GG_LOG_DEBUG("Topology size: %d", nodes.size());

            updated = true;
        }
        catch (GridClientException& e) {
        	last = e;
        }
    }

    if (!updated)
        GG_LOG_ERROR("Error refreshing the topology: %s", last.what());

    GG_LOG_DEBUG0("Finished refreshing the topology.");
}

void GridClientImpl::fireTopologyEvents(const TNodesSet& updNodes, const TNodesSet& prevNodes) {
    ClientNodeComparator nodeComp;

    TNodesSet newNodes;

    // Calculate new nodes.
    std::set_difference(updNodes.begin(), updNodes.end(), prevNodes.begin(), prevNodes.end(),
            std::inserter(newNodes, newNodes.begin()), nodeComp);

    // File onNodeAdded() events.
    for (auto i = newNodes.begin(); i != newNodes.end(); i++)
        fireNodeAdded(*i);

    TNodesSet leftNodes;

    // Calculate left nodes.
    std::set_difference(prevNodes.begin(), prevNodes.end(), updNodes.begin(), updNodes.end(),
            std::inserter(leftNodes, leftNodes.begin()), nodeComp);

    // Fire onNodeRemoved() events.
    for (auto i = leftNodes.begin(); i != leftNodes.end(); i++)
        fireNodeRemoved(*i);
}

void GridClientImpl::fireNodeAdded(const GridClientNode& node) {
    topLsnrsMux.lock();

    TGridClientTopologyListenerList locTopLsnrs = topLsnrs;

    topLsnrsMux.unlock();

    GG_LOG_DEBUG("Firing node added for node: %s", node.getNodeId().uuid().c_str());

    for (auto i = locTopLsnrs.begin(); i != locTopLsnrs.end(); i++) {
        TGridClientTopologyListenerPtr l = *i;

        try {
            l->onNodeAdded(node);
        }
        catch(std::exception& e) {
            GG_LOG_ERROR("Got exception from topology listener [what=%s]", e.what());
        }
        catch(...) {
            GG_LOG_ERROR0("Got unknown exception from topology listener.");
        }
    }
}

void GridClientImpl::fireNodeRemoved(const GridClientNode& node) {
    topLsnrsMux.lock();

    TGridClientTopologyListenerList locTopLsnrs = topLsnrs;

    topLsnrsMux.unlock();

    GG_LOG_DEBUG("Firing node left for node: %s", node.getNodeId().uuid().c_str());

    for (auto i = locTopLsnrs.begin(); i != locTopLsnrs.end(); i++) {
        TGridClientTopologyListenerPtr l = *i;

        try {
            l->onNodeRemoved(node);
        }
        catch(std::exception& e) {
            GG_LOG_ERROR("Got exception from topology listener [what=%s]", e.what());
        }
        catch(...) {
            GG_LOG_ERROR0("Got unknown exception from topology listener.");
        }
    }
}

void GridClientImpl::GridClientTopologyRefresher::onTimerEvent() {
    GG_LOG_DEBUG0("Timer event.");

    client.refreshTopology();
}

