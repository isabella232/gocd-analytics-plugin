/*
 * Copyright 2020 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.gocd.analytics.dao;

import com.thoughtworks.gocd.analytics.TestDBConnectionManager;
import com.thoughtworks.gocd.analytics.models.Agent;
import com.thoughtworks.gocd.analytics.models.AgentTransition;
import com.thoughtworks.gocd.analytics.utils.DateUtils;
import org.apache.ibatis.session.SqlSession;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.TimeZone;

import static com.thoughtworks.gocd.analytics.AgentMother.agentWith;
import static com.thoughtworks.gocd.analytics.utils.DateUtils.UTC;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class AgentTransitionDAOTest implements DAOIntegrationTest {
    private SqlSession sqlSession;
    private TestDBConnectionManager manager;
    private AgentTransitionDAO agentTransitionDAO;
    private AgentDAO agentDAO;

    @Before
    public void before() throws SQLException, InterruptedException {
        TimeZone.setDefault(TimeZone.getTimeZone(DateUtils.UTC));
        agentDAO = new AgentDAO();
        agentTransitionDAO = new AgentTransitionDAO();
        manager = new TestDBConnectionManager();
        sqlSession = manager.getSqlSession();
    }

    @After
    public void after() throws InterruptedException, SQLException {
        manager.shutdown();
    }

    @Test
    public void shouldInsertAgentTransition() {
        // an entry in agents table with the same uuid is required to avoid violation of foreign key constraint
        Agent agent = agentWith("uuid", "host_name", true, "127.0.0.1", "rh", "100G", ENABLED);
        agentDAO.updateOrInsert(sqlSession, agent);

        ZonedDateTime now = TEST_TS;

        AgentTransition transtion = agentTranstionWith("uuid", ENABLED, CANCELLED, CANCELLED, TEST_TS);

        agentTransitionDAO.insertTransition(sqlSession, transtion);

        List<AgentTransition> allTransitions = agentTransitionDAO.findByUuid(sqlSession, now.minusDays(7), now.plusDays(1), "uuid");

        assertThat(allTransitions.size(), is(1));

        assertThat(allTransitions.get(0).getUuid(), is("uuid"));
        assertThat(allTransitions.get(0).getAgentConfigState(), is(ENABLED));
        assertThat(allTransitions.get(0).getAgentState(), is(CANCELLED));
        assertThat(allTransitions.get(0).getBuildState(), is(CANCELLED));
        assertThat(allTransitions.get(0).getTransitionTime(), is(TEST_TS));
    }

    @Test
    public void shouldFindAgentTransitionsWithinTheProvidedDays() {
        // an entry in agents table with the same uuid is required to avoid violation of foreign key constraint
        Agent agent = agentWith("uuid", "host_name", true, "127.0.0.1", "rh", "100G", ENABLED);
        agentDAO.updateOrInsert(sqlSession, agent);

        AgentTransition on22 = agentTranstionWith("uuid", ENABLED, CANCELLED, CANCELLED, ZonedDateTime.parse("2018-03-22T12:34:56Z", DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(UTC));
        AgentTransition on23 = agentTranstionWith("uuid", ENABLED, CANCELLED, CANCELLED, ZonedDateTime.parse("2018-03-23T12:34:56Z", DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(UTC));
        AgentTransition on24 = agentTranstionWith("uuid", ENABLED, CANCELLED, CANCELLED, ZonedDateTime.parse("2018-03-24T12:34:56Z", DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(UTC));
        AgentTransition om25 = agentTranstionWith("uuid", ENABLED, CANCELLED, CANCELLED, ZonedDateTime.parse("2018-03-25T12:34:56Z", DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(UTC));

        agentTransitionDAO.insertTransition(sqlSession, on22);
        agentTransitionDAO.insertTransition(sqlSession, on23);
        agentTransitionDAO.insertTransition(sqlSession, on24);
        agentTransitionDAO.insertTransition(sqlSession, om25);

        ZonedDateTime startOn23 = ZonedDateTime.parse("2018-03-23T12:34:55Z", DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(UTC);
        ZonedDateTime endOn24 = ZonedDateTime.parse("2018-03-24T12:34:57Z", DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(UTC);

        List<AgentTransition> allTransitions = agentTransitionDAO.findByUuid(sqlSession, startOn23, endOn24, "uuid");

        assertThat(allTransitions.size(), is(2));

        assertThat(allTransitions.get(0), is(on23));
        assertThat(allTransitions.get(1), is(on24));
    }

    @Test
    public void shouldDeleteTransitionsPriorToProvidedTransitionTime() {
        // an entry in agents table with the same uuid is required to avoid violation of foreign key constraint
        Agent agent = agentWith("uuid", "host_name", true, "127.0.0.1", "rh", "100G", ENABLED);
        agentDAO.updateOrInsert(sqlSession, agent);

        AgentTransition on22 = agentTranstionWith("uuid", ENABLED, CANCELLED, CANCELLED, ZonedDateTime.parse("2018-03-22T12:34:56Z", DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(UTC));
        AgentTransition on23 = agentTranstionWith("uuid", ENABLED, CANCELLED, CANCELLED, ZonedDateTime.parse("2018-03-23T12:34:56Z", DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(UTC));
        AgentTransition on24 = agentTranstionWith("uuid", ENABLED, CANCELLED, CANCELLED, ZonedDateTime.parse("2018-03-24T12:34:56Z", DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(UTC));

        agentTransitionDAO.insertTransition(sqlSession, on22);
        agentTransitionDAO.insertTransition(sqlSession, on23);
        agentTransitionDAO.insertTransition(sqlSession, on24);


        ZonedDateTime dateOn22 = ZonedDateTime.parse("2018-03-22T12:34:55Z", DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(UTC);
        ZonedDateTime dateOn24 = ZonedDateTime.parse("2018-03-24T12:34:57Z", DateTimeFormatter.ISO_DATE_TIME).withZoneSameInstant(UTC);

        assertThat(agentTransitionDAO.findByUuid(sqlSession, dateOn22, dateOn24,"uuid").size(), is(3));

        agentTransitionDAO.deleteTransitionsPriorTo(sqlSession, ZonedDateTime.parse("2018-03-22T12:34:57Z", DateTimeFormatter.ISO_DATE_TIME));

        assertThat(agentTransitionDAO.findByUuid(sqlSession, dateOn22, dateOn24,"uuid").size(), is(2));
    }

    private AgentTransition agentTranstionWith(String uuid, String agentConfigState, String agentState, String buildState, ZonedDateTime transitionTime) {
        AgentTransition agentTransition = new AgentTransition();

        agentTransition.setUuid(uuid);
        agentTransition.setAgentConfigState(agentConfigState);
        agentTransition.setAgentState(agentState);
        agentTransition.setBuildState(buildState);
        agentTransition.setTransitionTime(transitionTime);

        return agentTransition;
    }
}
