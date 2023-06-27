/*
 * Copyright 2021 Andre Karalus
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.artofarc.esb.utils;

import java.util.List;
import java.util.Properties;

import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.ExecutionException;
import com.artofarc.esb.context.Context;
import com.artofarc.esb.context.ExecutionContext;
import com.artofarc.esb.context.WorkerPool;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.jms.JMSConnectionData;
import com.artofarc.esb.jms.JMSConnectionProvider;
import com.artofarc.esb.mbean.JMSConnectionGuardMXBean;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBMessage;

public class ReconnectJMSProviderAction extends Action {

	private final String _jndiConnectionFactory, _userName, _password, _workerPool;
	private JMSConnectionData _jmsConnectionData;

	public ReconnectJMSProviderAction(ClassLoader classLoader, Properties properties) {
		_pipelineStop = true;
		_jndiConnectionFactory = properties.getProperty("jndiConnectionFactory");
		if (_jndiConnectionFactory == null) {
			throw new IllegalArgumentException("jndiConnectionFactory is mandatory");
		}
		_userName = properties.getProperty("userName");
		_password = properties.getProperty("password");
		_workerPool = properties.getProperty("workerPool");
	}

	@Override
	protected void execute(Context context, ExecutionContext execContext, ESBMessage message, boolean nextActionIsPipelineStop) throws Exception {
		message.clearHeaders();
		if (_jmsConnectionData == null) {
			List<JMSConnectionData> jmsConnectionData = JMSConnectionData.create(context.getGlobalContext(), _jndiConnectionFactory, _userName, _password);
			if (jmsConnectionData.size() > 1) {
				throw new ExecutionException(this, "can only use one ConnectionFactory");
			}
			_jmsConnectionData = jmsConnectionData.get(0);
		}
		String workerPoolName = _workerPool != null ? _workerPool : message.getVariable("workerPool");
		WorkerPool workerPool = context.getGlobalContext().getWorkerPool(workerPoolName);
		if (workerPool == null) {
			throw new ExecutionException(this, "WorkerPool not found: " + workerPoolName);
		}
		JMSConnectionProvider jmsConnectionProvider = workerPool.getPoolContext().getResourceFactory(JMSConnectionProvider.class);
		JMSConnectionGuardMXBean jmsConnectionGuard = jmsConnectionProvider.getResource(_jmsConnectionData);
		jmsConnectionGuard.reconnect();
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, HttpConstants.HTTP_HEADER_CONTENT_TYPE_TEXT);
		String reconnectInterval = System.getProperty("esb0.jms.reconnectInterval", "60");
		message.reset(BodyType.STRING, "Reconnecting '" + _jmsConnectionData + "' for WorkerPool '" + workerPool.getName() + "' in " + reconnectInterval + "s");
	}

}
