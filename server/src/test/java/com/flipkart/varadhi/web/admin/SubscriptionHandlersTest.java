package com.flipkart.varadhi.web.admin;

import com.flipkart.varadhi.core.VaradhiTopicService;
import com.flipkart.varadhi.entities.*;
import com.flipkart.varadhi.exceptions.ResourceNotFoundException;
import com.flipkart.varadhi.services.ProjectService;
import com.flipkart.varadhi.services.SubscriptionService;
import com.flipkart.varadhi.utils.VaradhiSubscriptionFactory;
import com.flipkart.varadhi.web.ErrorResponse;
import com.flipkart.varadhi.web.WebTestBase;
import com.flipkart.varadhi.web.v1.admin.SubscriptionHandlers;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.client.HttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class SubscriptionHandlersTest extends WebTestBase {
    private static final Endpoint endpoint;
    private static final RetryPolicy retryPolicy = new RetryPolicy(
            new CodeRange[]{new CodeRange(500, 502)},
            RetryPolicy.BackoffType.LINEAR,
            1, 1, 1, 1
    );
    private static final ConsumptionPolicy consumptionPolicy = new ConsumptionPolicy(1, 1, false, 1, null);
    private static final TopicCapacityPolicy capacityPolicy = new TopicCapacityPolicy(1, 10, 1);
    private static final SubscriptionShards shards = new SubscriptionUnitShard(0, capacityPolicy, null, null, null);

    static {
        try {
            endpoint = new Endpoint.HttpEndpoint(new URL("http", "localhost", "hello"), "GET", "", 500, 500, false);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private final Project project = new Project("project1", 0, "", "team1", "org1");
    private final TopicResource topicResource = new TopicResource("topic1", 0, "project2", false, null);
    SubscriptionHandlers subscriptionHandlers;
    SubscriptionService subscriptionService;
    ProjectService projectService;
    VaradhiTopicService topicService;
    VaradhiSubscriptionFactory subscriptionFactory;

    public static VaradhiSubscription getVaradhiSubscription(
            String subscriptionName, Project project, VaradhiTopic topic, int version
    ) {
        return getVaradhiSubscription(subscriptionName, false, project, topic, version);
    }

    public static VaradhiSubscription getVaradhiSubscription(
            String subscriptionName, boolean grouped, Project project, VaradhiTopic topic, int version
    ) {
        VaradhiSubscription subscription = VaradhiSubscription.of(
                SubscriptionResource.buildInternalName(project.getName(), subscriptionName),
                project.getName(),
                topic.getName(),
                UUID.randomUUID().toString(),
                grouped,
                endpoint,
                retryPolicy,
                consumptionPolicy,
                shards
        );
        subscription.setVersion(version);
        return subscription;
    }

    @BeforeEach
    public void PreTest() throws InterruptedException {
        super.setUp();
        subscriptionService = mock(SubscriptionService.class);
        projectService = mock(ProjectService.class);
        topicService = mock(VaradhiTopicService.class);
        subscriptionFactory = mock(VaradhiSubscriptionFactory.class);
        subscriptionHandlers =
                new SubscriptionHandlers(subscriptionService, projectService, topicService, subscriptionFactory);

        Route routeCreate = router.post("/projects/:project/subscriptions").handler(bodyHandler).handler(ctx -> {
                    subscriptionHandlers.setSubscription(ctx);
                    ctx.next();
                })
                .handler(wrapBlocking(subscriptionHandlers::create));
        setupFailureHandler(routeCreate);

        Route routeGet = router.get("/projects/:project/subscriptions/:subscription")
                .handler(wrapBlocking(subscriptionHandlers::get));
        setupFailureHandler(routeGet);

        Route routeListAll =
                router.get("/projects/:project/subscriptions").handler(wrapBlocking(subscriptionHandlers::list));
        setupFailureHandler(routeListAll);

        Route routeDelete = router.delete("/projects/:project/subscriptions/:subscription")
                .handler(wrapBlocking(subscriptionHandlers::delete));
        setupFailureHandler(routeDelete);

        Route routeUpdate = router.put("/projects/:project/subscriptions/:subscription").handler(bodyHandler)
                .handler(ctx -> {
                    subscriptionHandlers.setSubscription(ctx);
                    ctx.next();
                })
                .handler(wrapBlocking(subscriptionHandlers::update));
        setupFailureHandler(routeUpdate);
    }

    @AfterEach
    public void PostTest() throws InterruptedException {
        super.tearDown();
    }

    @Test
    void testSubscriptionCreate() throws InterruptedException {
        HttpRequest<Buffer> request = createRequest(HttpMethod.POST, getSubscriptionsUrl(project));
        SubscriptionResource resource = getSubscriptionResource("sub12", project, topicResource);
        VaradhiTopic vTopic = VaradhiTopic.of(topicResource);
        doReturn(vTopic).when(topicService).get(topicResource.getProject() + "." + topicResource.getName());

        VaradhiSubscription subscription = getVaradhiSubscription("sub12", project, vTopic, 0);
        when(subscriptionService.createSubscription(any(), any(), any())).thenReturn(subscription);
        SubscriptionResource created = sendRequestWithBody(request, resource, SubscriptionResource.class);
        assertEquals(subscription.getName(), created.getSubscriptionInternalName());
    }

    @Test
    void testCreateSubscriptionWithNonExistentProject() throws InterruptedException {
        HttpRequest<Buffer> request = createRequest(HttpMethod.POST, getSubscriptionsUrl(project));
        VaradhiTopic vTopic = VaradhiTopic.of(topicResource);
        SubscriptionResource resource = getSubscriptionResource("sub12", project, topicResource);
        VaradhiSubscription subscription = getVaradhiSubscription("sub12", project, vTopic, 0);

        doReturn(vTopic).when(topicService).get(topicResource.getProject() + "." + topicResource.getName());
        doReturn(subscription).when(subscriptionFactory).get(any(), any(), any());
        String errMsg = "Project not found.";
        doThrow(new ResourceNotFoundException(errMsg)).when(projectService).getCachedProject(project.getName());

        ErrorResponse resp = sendRequestWithBody(request, resource, 404, errMsg, ErrorResponse.class);
        assertEquals(errMsg, resp.reason());
    }

    @Test
    void testCreateSubscriptionWithNonExistentTopic() throws InterruptedException {
        HttpRequest<Buffer> request = createRequest(HttpMethod.POST, getSubscriptionsUrl(project));
        VaradhiTopic vTopic = VaradhiTopic.of(topicResource);
        SubscriptionResource resource = getSubscriptionResource("sub12", project, topicResource);
        VaradhiSubscription subscription = getVaradhiSubscription("sub12", project, vTopic, 0);

        doReturn(subscription).when(subscriptionFactory).get(any(), any(), any());
        doReturn(project).when(projectService).getCachedProject(project.getName());
        String errMsg = "Topic not found.";
        doThrow(new ResourceNotFoundException(errMsg)).when(topicService)
                .get(topicResource.getProject() + "." + topicResource.getName());

        ErrorResponse resp = sendRequestWithBody(request, resource, 404, errMsg, ErrorResponse.class);
        assertEquals(errMsg, resp.reason());
    }

    @Test
    void testSubscriptionCreateInconsistentProjectNameFailure() throws InterruptedException {
        HttpRequest<Buffer> request = createRequest(HttpMethod.POST, getSubscriptionsUrl(project));
        SubscriptionResource resource =
                getSubscriptionResource("sub1", new Project("project2", 0, "", "team1", "org1"), topicResource);

        String errMsg = "Specified Project name is different from Project name in url";
        ErrorResponse resp = sendRequestWithBody(request, resource, 400, errMsg, ErrorResponse.class);
        assertEquals(errMsg, resp.reason());
    }

    @Test
    void testSubscriptionGet() throws InterruptedException {
        HttpRequest<Buffer> request = createRequest(HttpMethod.GET, getSubscriptionUrl("sub12", project));
        SubscriptionResource resource = getSubscriptionResource("sub12", project, topicResource);

        VaradhiSubscription subscription = getVaradhiSubscription("sub12", project, VaradhiTopic.of(topicResource), 0);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        when(subscriptionService.getSubscription(captor.capture())).thenReturn(subscription);

        SubscriptionResource got = sendRequestWithoutBody(request, SubscriptionResource.class);
        assertEquals(got.getName(), resource.getName());
        assertEquals(captor.getValue(), resource.getSubscriptionInternalName());
    }

    @Test
    void testListSubscription() throws InterruptedException {
        HttpRequest<Buffer> request = createRequest(HttpMethod.GET, getSubscriptionsUrl(project));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        when(subscriptionService.getSubscriptionList(captor.capture()))
                .thenReturn(List.of("sub1", "sub2"))
                .thenReturn(List.of());

        List<String> got = sendRequestWithoutBody(request, List.class);
        assertEquals(List.of("sub1", "sub2"), got);
        assertEquals(project.getName(), captor.getValue());

        List<String> got2 = sendRequestWithoutBody(request, List.class);
        assertEquals(List.of(), got2);
    }

    @Test
    void testSubscriptionDelete() throws InterruptedException {
        HttpRequest<Buffer> request = createRequest(HttpMethod.DELETE, getSubscriptionUrl("sub1", project));
        SubscriptionResource resource = getSubscriptionResource("sub1", project, topicResource);

        doReturn(project).when(projectService).getCachedProject(project.getName());
        ArgumentCaptor<String> captorSubName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Project> captorProject = ArgumentCaptor.forClass(Project.class);
        doReturn(CompletableFuture.completedFuture(null)).when(subscriptionService)
                .deleteSubscription(captorSubName.capture(), captorProject.capture(), any());

        sendRequestWithoutBody(request, null);
        assertEquals(captorSubName.getValue(), resource.getSubscriptionInternalName());
        assertEquals(captorProject.getValue().getName(), project.getName());
        verify(subscriptionService, times(1)).deleteSubscription(any(), any(), any());
    }

    @Test
    void testSubscriptionUpdate() throws InterruptedException {
        HttpRequest<Buffer> request = createRequest(HttpMethod.PUT, getSubscriptionUrl("sub1", project));
        SubscriptionResource resource = getSubscriptionResource("sub1", project, topicResource);

        VaradhiTopic vTopic = VaradhiTopic.of(topicResource);
        doReturn(vTopic).when(topicService).get(topicResource.getProject() + "." + topicResource.getName());

        VaradhiSubscription subscription = getVaradhiSubscription("sub1", project, vTopic, 2);
        doReturn(subscription).when(subscriptionFactory).get(any(), any(), any());
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> versionCaptor = ArgumentCaptor.forClass(Integer.class);
        when(subscriptionService.updateSubscription(
                nameCaptor.capture(), versionCaptor.capture(), anyString(), anyBoolean(), any(), any(), any(),
                any()
        )).thenReturn(
                CompletableFuture.completedFuture(subscription));

        SubscriptionResource updated = sendRequestWithBody(request, resource, SubscriptionResource.class);
        assertEquals(resource.getName(), updated.getName());
        assertEquals(resource.getSubscriptionInternalName(), nameCaptor.getValue());
        assertEquals(1, versionCaptor.getValue());
    }

    private String getSubscriptionsUrl(Project project) {
        return String.join("/", "/projects", project.getName(), "subscriptions");
    }

    private String getSubscriptionUrl(String subscriptionName, Project project) {
        return String.join("/", getSubscriptionsUrl(project), subscriptionName);
    }

    private SubscriptionResource getSubscriptionResource(
            String subscriptionName, Project project, TopicResource topic
    ) {
        return new SubscriptionResource(
                subscriptionName,
                1,
                project.getName(),
                topic.getName(),
                topic.getProject(),
                "desc",
                false,
                endpoint,
                retryPolicy,
                consumptionPolicy
        );
    }

}
