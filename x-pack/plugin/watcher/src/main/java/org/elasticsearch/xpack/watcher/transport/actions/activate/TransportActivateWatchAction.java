/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.watcher.transport.actions.activate;

import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.routing.Preference;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.license.XPackLicenseState;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.elasticsearch.xpack.core.watcher.transport.actions.activate.ActivateWatchAction;
import org.elasticsearch.xpack.core.watcher.transport.actions.activate.ActivateWatchRequest;
import org.elasticsearch.xpack.core.watcher.transport.actions.activate.ActivateWatchResponse;
import org.elasticsearch.xpack.core.watcher.watch.Watch;
import org.elasticsearch.xpack.core.watcher.watch.WatchField;
import org.elasticsearch.xpack.core.watcher.watch.WatchStatus;
import org.elasticsearch.xpack.watcher.transport.actions.WatcherTransportAction;
import org.elasticsearch.xpack.watcher.trigger.TriggerService;
import org.elasticsearch.xpack.watcher.watch.WatchParser;
import org.joda.time.DateTime;

import java.io.IOException;
import java.time.Clock;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.xpack.core.ClientHelper.WATCHER_ORIGIN;
import static org.elasticsearch.xpack.core.ClientHelper.executeAsyncWithOrigin;
import static org.elasticsearch.xpack.core.watcher.support.WatcherDateTimeUtils.writeDate;
import static org.joda.time.DateTimeZone.UTC;

/**
 * Performs the watch de/activation operation.
 */
public class TransportActivateWatchAction extends WatcherTransportAction<ActivateWatchRequest, ActivateWatchResponse> {

    private final Clock clock;
    private final WatchParser parser;
    private final Client client;
    private final TriggerService triggerService;
    private final ClusterService clusterService;

    @Inject
    public TransportActivateWatchAction(Settings settings, TransportService transportService, ThreadPool threadPool,
                                        ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver, Clock clock,
                                        XPackLicenseState licenseState, WatchParser parser, ClusterService clusterService,
                                        Client client, TriggerService triggerService) {
        super(settings, ActivateWatchAction.NAME, transportService, threadPool, actionFilters, indexNameExpressionResolver,
                licenseState, clusterService, ActivateWatchRequest::new, ActivateWatchResponse::new);
        this.clock = clock;
        this.parser = parser;
        this.clusterService = clusterService;
        this.client = client;
        this.triggerService = triggerService;
    }

    @Override
    protected void masterOperation(ActivateWatchRequest request, ClusterState state, ActionListener<ActivateWatchResponse> listener)
            throws Exception {

        try {
            DateTime now = new DateTime(clock.millis(), UTC);
            UpdateRequest updateRequest = new UpdateRequest(Watch.INDEX, Watch.DOC_TYPE, request.getWatchId());
            updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            XContentBuilder builder = activateWatchBuilder(request.isActivate(), now);
            updateRequest.doc(builder);
            // a watch execution updates the status in between, we still want this want to override the active state
            // two has been chosen arbitrary, maybe one would make more sense, as a watch would not execute more often than
            // once per second?
            updateRequest.retryOnConflict(2);

            executeAsyncWithOrigin(client.threadPool().getThreadContext(), WATCHER_ORIGIN, updateRequest,
                    ActionListener.<UpdateResponse>wrap(updateResponse -> {
                GetRequest getRequest = new GetRequest(Watch.INDEX, Watch.DOC_TYPE, request.getWatchId())
                        .preference(Preference.LOCAL.type()).realtime(true);

                executeAsyncWithOrigin(client.threadPool().getThreadContext(), WATCHER_ORIGIN, getRequest,
                        ActionListener.<GetResponse>wrap(getResponse -> {
                            if (getResponse.isExists()) {
                                Watch watch = parser.parseWithSecrets(request.getWatchId(), true, getResponse.getSourceAsBytesRef(), now,
                                        XContentType.JSON, getResponse.getSeqNo(), getResponse.getPrimaryTerm());
                                watch.status().version(getResponse.getVersion());
                                // if we are not yet running in distributed mode, only call triggerservice, if we are on the master node
                                if (localExecute(request) == false && this.clusterService.state().nodes().isLocalNodeElectedMaster()) {
                                    if (watch.status().state().isActive()) {
                                        triggerService.add(watch);
                                    } else {
                                        triggerService.remove(watch.id());
                                    }
                                }
                                listener.onResponse(new ActivateWatchResponse(watch.status()));
                            } else {
                                listener.onFailure(new ResourceNotFoundException("Watch with id [{}] does not exist",
                                        request.getWatchId()));
                            }
                        }, listener::onFailure), client::get);
            }, listener::onFailure), client::update);
        } catch (IOException e) {
            listener.onFailure(e);
        }
    }

    private XContentBuilder activateWatchBuilder(boolean active, DateTime now) throws IOException {
        try (XContentBuilder builder = jsonBuilder()) {
            builder.startObject()
                    .startObject(WatchField.STATUS.getPreferredName())
                    .startObject(WatchStatus.Field.STATE.getPreferredName())
                    .field(WatchStatus.Field.ACTIVE.getPreferredName(), active);

            writeDate(WatchStatus.Field.TIMESTAMP.getPreferredName(), builder, now);
            builder.endObject().endObject().endObject();
            return builder;
        }
    }

}
