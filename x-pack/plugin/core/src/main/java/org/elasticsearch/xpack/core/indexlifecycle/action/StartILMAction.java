/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.core.indexlifecycle.action;

import org.elasticsearch.action.Action;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.xpack.core.indexlifecycle.StartILMRequest;

public class StartILMAction extends Action<StartILMRequest, AcknowledgedResponse, StartILMActionRequestBuilder> {
    public static final StartILMAction INSTANCE = new StartILMAction();
    public static final String NAME = "cluster:admin/ilm/start";

    protected StartILMAction() {
        super(NAME);
    }

    @Override
    public AcknowledgedResponse newResponse() {
        return new AcknowledgedResponse();
    }

    @Override
    public StartILMActionRequestBuilder newRequestBuilder(final ElasticsearchClient client) {
        return new StartILMActionRequestBuilder(client, INSTANCE);
    }

}
