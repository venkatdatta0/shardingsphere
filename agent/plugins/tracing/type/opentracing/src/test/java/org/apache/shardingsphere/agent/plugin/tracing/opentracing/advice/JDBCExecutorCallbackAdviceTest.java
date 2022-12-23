/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.agent.plugin.tracing.opentracing.advice;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import org.apache.shardingsphere.agent.advice.MethodInvocationResult;
import org.apache.shardingsphere.agent.plugin.tracing.opentracing.constant.ErrorLogTagKeys;
import org.apache.shardingsphere.infra.executor.sql.context.ExecutionUnit;
import org.apache.shardingsphere.infra.executor.sql.context.SQLUnit;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.driver.jdbc.JDBCExecutionUnit;
import org.apache.shardingsphere.infra.executor.sql.execute.engine.driver.jdbc.JDBCExecutorCallback;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.internal.configuration.plugins.Plugins;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class JDBCExecutorCallbackAdviceTest {
    
    private static MockTracer tracer;
    
    private static Method executeMethod;
    
    @BeforeClass
    public static void setup() throws ReflectiveOperationException {
        if (!GlobalTracer.isRegistered()) {
            GlobalTracer.register(new MockTracer());
        }
        tracer = (MockTracer) Plugins.getMemberAccessor().get(GlobalTracer.class.getDeclaredField("tracer"), GlobalTracer.get());
        executeMethod = JDBCExecutorCallback.class.getDeclaredMethod("execute", JDBCExecutionUnit.class, boolean.class, Map.class);
    }
    
    @Before
    public void reset() {
        tracer.reset();
    }
    
    @Test
    public void assertMethod() {
        MockTargetAdviceObject targetObject = new MockTargetAdviceObject();
        Map<String, Object> extraMap = Collections.singletonMap("_root_span_", null);
        JDBCExecutionUnit executionUnit = mock(JDBCExecutionUnit.class);
        when(executionUnit.getExecutionUnit()).thenReturn(new ExecutionUnit("mock.db", new SQLUnit("select 1", Collections.emptyList())));
        JDBCExecutorCallbackAdvice advice = new JDBCExecutorCallbackAdvice();
        advice.beforeMethod(targetObject, executeMethod, new Object[]{executionUnit, false, extraMap}, new MethodInvocationResult());
        advice.afterMethod(targetObject, executeMethod, new Object[]{executionUnit, false, extraMap}, new MethodInvocationResult());
        List<MockSpan> spans = tracer.finishedSpans();
        assertThat(spans.size(), is(1));
        MockSpan span = spans.get(0);
        Map<String, Object> tags = span.tags();
        assertTrue(spans.get(0).logEntries().isEmpty());
        assertThat(span.operationName(), is("/ShardingSphere/executeSQL/"));
        assertThat(tags.get("db.instance"), is("mock.db"));
        assertThat(tags.get("db.type"), is("sql"));
        assertThat(tags.get("span.kind"), is("client"));
        assertThat(tags.get("db.statement"), is("select 1"));
    }
    
    @Test
    public void assertExceptionHandle() {
        MockTargetAdviceObject targetObject = new MockTargetAdviceObject();
        Map<String, Object> extraMap = Collections.singletonMap("_root_span_", null);
        JDBCExecutionUnit executionUnit = mock(JDBCExecutionUnit.class);
        when(executionUnit.getExecutionUnit()).thenReturn(new ExecutionUnit("mock.db", new SQLUnit("select 1", Collections.emptyList())));
        JDBCExecutorCallbackAdvice advice = new JDBCExecutorCallbackAdvice();
        advice.beforeMethod(targetObject, executeMethod, new Object[]{executionUnit, false, extraMap}, new MethodInvocationResult());
        advice.onThrowing(targetObject, executeMethod, new Object[]{executionUnit, false, extraMap}, new IOException());
        advice.afterMethod(targetObject, executeMethod, new Object[]{executionUnit, false, extraMap}, new MethodInvocationResult());
        List<MockSpan> spans = tracer.finishedSpans();
        assertThat(spans.size(), is(1));
        MockSpan span = spans.get(0);
        List<MockSpan.LogEntry> entries = span.logEntries();
        Map<String, ?> fields = entries.get(0).fields();
        assertThat(fields.get(ErrorLogTagKeys.EVENT), is("error"));
        assertThat(fields.get(ErrorLogTagKeys.ERROR_KIND), is("java.io.IOException"));
        Map<String, Object> tags = span.tags();
        assertThat(span.operationName(), is("/ShardingSphere/executeSQL/"));
        assertThat(tags.get("db.instance"), is("mock.db"));
        assertThat(tags.get("db.type"), is("sql"));
        assertThat(tags.get("span.kind"), is("client"));
        assertThat(tags.get("db.statement"), is("select 1"));
    }
}
