package org.openhab.binding.qingping.internal.sync;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.qingping.internal.client.http.QingpingClient;
import org.openhab.binding.qingping.internal.client.http.QingpingServiceInteractionException;
import org.openhab.binding.qingping.internal.client.http.dto.device.list.Device;
import org.openhab.binding.qingping.internal.client.http.dto.device.list.DeviceListResponse;
import org.openhab.binding.qingping.internal.client.http.dto.device.list.info.DeviceInfo;

import io.netty.util.concurrent.ScheduledFuture;

@ExtendWith(MockitoExtension.class)
@DisplayName("QingpingThingsStateUpdater")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QingpingThingsStateUpdaterTest {
    @Mock
    private QingpingClient qingpingClient;
    @Mock
    private ScheduledExecutorService scheduler;
    @Captor
    private ArgumentCaptor<Runnable> runnableArgumentCaptor;
    private QingpingThingsStateUpdater qingpingThingsStateUpdater;

    @BeforeEach
    void setUp() {
        qingpingThingsStateUpdater = new QingpingThingsStateUpdater(qingpingClient, scheduler);
    }

    @Test
    @SuppressWarnings({ "unchecked", "null" })
    void should_register_state_handler_for_things_and_deregister_it() throws QingpingServiceInteractionException {
        // Creating test data
        @SuppressWarnings("rawtypes")
        final ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        when(scheduler.scheduleAtFixedRate(any(), anyLong(), anyLong(), any())).thenReturn(scheduledFuture);

        final String deviceMac1 = "DM1";
        final String deviceMac2 = "DM2";
        @SuppressWarnings("DataFlowIssue")
        final Device device1 = new Device(new DeviceInfo(deviceMac1, null, null, null, 0, 0, null, null, null), null);
        @SuppressWarnings("DataFlowIssue")
        final Device device2 = new Device(new DeviceInfo(deviceMac2, null, null, null, 0, 0, null, null, null), null);
        final List<Device> devices = List.of(device1, device2);
        when(qingpingClient.listDevices()).thenReturn(new DeviceListResponse(2, devices));

        // Checking state refresh action scheduling
        final Consumer<Device> singleDeviceConsumer = mock(Consumer.class);
        final Runnable registration1 = qingpingThingsStateUpdater.subscribeForSingleDevice(deviceMac1,
                singleDeviceConsumer);
        verify(scheduler).scheduleAtFixedRate(runnableArgumentCaptor.capture(), eq(0L), eq(30L), eq(TimeUnit.SECONDS));
        final Runnable registration2 = qingpingThingsStateUpdater.subscribeForSingleDevice(deviceMac2,
                singleDeviceConsumer);
        verifyNoMoreInteractions(scheduler);
        final Consumer<Collection<Device>> allDevicesConsumer = mock(Consumer.class);
        final Runnable allSubscription = qingpingThingsStateUpdater.subscribeForAllDevices(allDevicesConsumer);

        // Checking consumer interaction during scheduled action execution
        final Runnable scheduledAction = runnableArgumentCaptor.getValue();
        scheduledAction.run();
        verify(singleDeviceConsumer).accept(device1);
        verify(singleDeviceConsumer).accept(device2);
        verify(allDevicesConsumer).accept(devices);

        registration1.run();
        registration2.run();
        allSubscription.run();

        verify(scheduledFuture).cancel(false);
    }

    @Test
    void should_not_stop_syncing_when_only_single_device_subscription_is_present() {
        // Prepare test data
        @SuppressWarnings("rawtypes")
        final ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        // noinspection unchecked
        when(scheduler.scheduleAtFixedRate(any(), anyLong(), anyLong(), any())).thenReturn(scheduledFuture);

        // Subsribing
        @SuppressWarnings("unchecked")
        final Consumer<Device> singleDeviceConsumer = mock(Consumer.class);
        qingpingThingsStateUpdater.subscribeForSingleDevice("anyMac", singleDeviceConsumer);

        @SuppressWarnings("unchecked")
        final Consumer<Collection<Device>> allDevicesConsumer = mock(Consumer.class);
        final Runnable allSubscription = qingpingThingsStateUpdater.subscribeForAllDevices(allDevicesConsumer);

        // Unsubscribing
        allSubscription.run();

        // Verification
        verify(scheduledFuture, never()).cancel(false);
    }

    @Test
    void should_not_stop_syncing_when_only_all_devices_subscription_is_present() {
        // Prepare test data
        @SuppressWarnings("rawtypes")
        final ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
        // noinspection unchecked
        when(scheduler.scheduleAtFixedRate(any(), anyLong(), anyLong(), any())).thenReturn(scheduledFuture);

        // Subsribing
        @SuppressWarnings("unchecked")
        final Consumer<Device> singleDeviceConsumer = mock(Consumer.class);
        final Runnable singleDeviceSubscription = qingpingThingsStateUpdater.subscribeForSingleDevice("anyMac",
                singleDeviceConsumer);

        @SuppressWarnings("unchecked")
        final Consumer<Collection<Device>> allDevicesConsumer = mock(Consumer.class);
        qingpingThingsStateUpdater.subscribeForAllDevices(allDevicesConsumer);

        // Unsubscribing
        singleDeviceSubscription.run();

        // Verification
        verify(scheduledFuture, never()).cancel(false);
    }
}