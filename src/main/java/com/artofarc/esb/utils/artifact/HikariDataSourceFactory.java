/*
 * Copyright 2022 Andre Karalus
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
package com.artofarc.esb.utils.artifact;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Properties;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;

public class HikariDataSourceFactory implements com.artofarc.esb.artifact.JNDIObjectFactoryArtifact.Factory {

	private final static Object[] EMPTY_OBJECT_ARRAY = new Object[0];

	private HikariConfig _hikariConfig;

	@Override
	public void validate(Class<?> type, Properties properties) {
		if (!type.isAssignableFrom(HikariDataSource.class)) {
			throw new IllegalArgumentException("HikariDataSource cannot be assigned to " + type);
		}
		_hikariConfig = new HikariConfig(properties);
		_hikariConfig.validate();
	}

	@Override
	public boolean tryUpdate(Object oldDataSource) {
		if (oldDataSource instanceof HikariDataSource) {
			outer: for (Method method : HikariConfig.class.getDeclaredMethods()) {
				String name = method.getName();
				if (Modifier.isPublic(method.getModifiers()) && (name.startsWith("get") || name.startsWith("is"))) {
					try {
						Object newValue = method.invoke(_hikariConfig, EMPTY_OBJECT_ARRAY);
						if (!Objects.equals(method.invoke(oldDataSource, EMPTY_OBJECT_ARRAY), newValue)) {
							for (Method methodMX : HikariConfigMXBean.class.getMethods()) {
								if (methodMX.getParameterCount() == 1 && methodMX.getName().endsWith(name.substring(2))) {
									methodMX.invoke(oldDataSource, newValue);
									continue outer;
								}
							}
							return false;
						}
					} catch (ReflectiveOperationException e) {
						throw new RuntimeException(e);
					}
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public HikariDataSource createObject() {
		return new HikariDataSource(_hikariConfig);
	}

}
