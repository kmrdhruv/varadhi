package com.flipkart.varadhi.spi.db;

import com.flipkart.varadhi.auth.RoleBindingNode;
import com.flipkart.varadhi.entities.*;

import java.util.List;

public interface MetaStore {
    void createOrg(Org org);

    Org getOrg(String orgName);

    boolean checkOrgExists(String orgName);

    void deleteOrg(String orgName);

    List<Org> getOrgs();

    List<String> getTeamNames(String orgName);

    List<Team> getTeams(String orgName);

    void createTeam(Team team);

    Team getTeam(String teamName, String orgName);

    boolean checkTeamExists(String teamName, String orgName);

    void deleteTeam(String teamName, String orgName);

    List<Project> getProjects(String teamName, String orgName);

    void createProject(Project project);

    Project getProject(String projectName);

    boolean checkProjectExists(String projectName);

    void deleteProject(String project);

    int updateProject(Project project);

    List<String> getVaradhiTopicNames(String projectName);

    void createTopicResource(TopicResource topicResourceName);

    TopicResource getTopicResource(String topicResourceName, String projectName);

    boolean checkTopicResourceExists(String topicName, String projectName);

    void deleteTopicResource(String topicResourceName, String projectName);

    void createVaradhiTopic(VaradhiTopic varadhiTopicName);

    VaradhiTopic getVaradhiTopic(String varadhiTopicName);

    boolean checkVaradhiTopicExists(String varadhiTopicName);

    void deleteVaradhiTopic(String varadhiTopicName);

    List<RoleBindingNode> getRoleBindingNodes();

    RoleBindingNode findRoleBindingNode(String resourceIdWithType);

    RoleBindingNode getRoleBindingNode(ResourceType resourceType, String resourceId);

    void createRoleBindingNode(RoleBindingNode node);

    boolean checkRoleBindingNodeExists(ResourceType resourceType, String resourceId);

    int updateRoleBindingNode(RoleBindingNode node);

    void deleteRoleBindingNode(ResourceType resourceType, String resourceId);
}
