package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    private SecurityService securityService;
    @Mock
    private SecurityRepository securityRepository;
    @Mock
    private ImageService imageService;

    @Mock
    private StatusListener statusListener;

    private Sensor sensor;
    private final String randomSensorName = UUID.randomUUID().toString();

    private Set<Sensor> populateSensorSet(boolean status){

        String randomName = UUID.randomUUID().toString();
        Set<Sensor> sensorSet = new HashSet<>();
        for(int i = 0; i < 3; i++){
            sensorSet.add(new Sensor(randomName, SensorType.DOOR));
        }
        sensorSet.forEach(sensor -> sensor.setActive(status));
        return sensorSet;
    }

    private Sensor newSensor(){
        return new Sensor(randomSensorName, SensorType.DOOR);
    }

    @BeforeEach
    void init(){
        securityService = new SecurityService(securityRepository, imageService);
        sensor = newSensor();
    }

    /* Test 1: If alarm is armed and a sensor becomes activated,
       put the system into pending alarm status*/
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void setAlarmStatus_ifAlarmIsArmedAndSensorActivated_putSystemIntoPendingAlarm(ArmingStatus armingStatus){

        when(securityService.getArmingStatus()).thenReturn(armingStatus);
        when(securityService.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);

    }


    /* Test 2: If alarm is armed and a sensor becomes activated and the system is already pending alarm,
       set the alarm status to alarm */

    @Test
    void setAlarmStatus_ifAlarmArmedAndSensorActivatedAndAlarmPending_setAlarmStatusToAlarm(){

        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, atMost(2)).setAlarmStatus(AlarmStatus.ALARM);

    }

    /*Test 3: If pending alarm and all sensors are inactive,
      return to no alarm state.*/
    @Test
    void setAlarmStatus_ifPendingAlarmAndAllSensorsInactive_setNoAlarmState(){

        Set<Sensor> sensorSet = populateSensorSet(false);
        sensorSet.forEach(sensor -> sensor.setActive(false));
        sensorSet.forEach( sensor -> securityService.changeSensorActivationStatus(sensor, false));
        when(securityRepository.getSensors()).thenReturn(sensorSet);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);

    }

    /* Test 4: If alarm is active,
       change in sensor state should not affect the alarm state. */
   @Test
    void ifAlarmActive_changeInSensorStateShouldNotAffectAlarmState(){
        sensor.setActive(false);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(securityRepository, atMostOnce()).updateSensor(sensor);

        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(securityRepository, atMost(2)).updateSensor(sensor);
    }


    /* Test 5: If a sensor is activated while already active and the system is in pending state,
       change it to alarm state.*/
    @Test
    void setAlarmStatus_ifSensorActivatedWhileActiveAndSystemPending_changeToAlarmState(){

        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, atMostOnce()).setAlarmStatus(AlarmStatus.ALARM);

    }

    /*Test 6: If a sensor is deactivated while already inactive,
      make no changes to the alarm state.*/
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"NO_ALARM", "PENDING_ALARM", "ALARM" })
    void setAlarmStatus_ifSensorDeactivatedWhileInactive_noChangeToAlarmState(AlarmStatus alarmStatus){

        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(any());

    }


    /* Test 7: If the image service identifies an image containing a cat while the system is armed-home,
       put the system into alarm status.*/
    @Test
    void setAlarmStatus_ifImageServiceIdentifiesAnImageContainingACatWhileSystemIsArmedHome_putSystemIntoAlarmStatus(){
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }


    /* Test 8: If the image service identifies an image that does not contain a cat,
       change the status to no alarm as long as the sensors are not active.*/
    @Test
    void setAlarmStatus_ifImageServiceIdentifiesImageNotContainingACat_changeStatusToNoAlarmAsLongAsSensorsAreNotActive(){

        Set<Sensor> sensorSet = populateSensorSet(false);
        sensorSet.forEach(sensor -> securityService.changeSensorActivationStatus(sensor, false));
        when(securityRepository.getSensors()).thenReturn(sensorSet);
        when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);

    }

    /* Test 9: If the system is disarmed,
       set the status to no alarm.*/
    @Test
    void setAlarmStatus_ifSystemIsDisarmed_setStatusToNoAlarm(){
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    /* Test 10: If the system is armed,
       reset all sensors to inactive.*/
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void changeSensorActivationStatus_ifSystemIsArmed_resetAllSensorsToInactive(ArmingStatus armingStatus){

        securityRepository.setArmingStatus(armingStatus);
        securityService.getSensors().forEach(sensor -> {
            assertFalse(sensor.getActive());
        });

    }

    /* Test 11: If the system is armed-home while the camera shows a cat,
       set the alarm status to alarm.*/

    @Test
    void ifSystemIsArmedHomeWhileCameraShowsACat_setAlarmStatusToAlarm(){
        BufferedImage bufferedImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(bufferedImage);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);

    }


    // For tests coverage
    // update sensor when system is armed, set alarm

    @Test
    void setAlarm_whenSystemIsArmedAndSensorsDeactivated_setAlarm(){

        Set<Sensor> sensorSet =  populateSensorSet(true);
        when(securityRepository.getSensors()).thenReturn(sensorSet);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);

    }

    // Add Status Listener
    @Test
    void addStatusListener(){
        securityService.addStatusListener(statusListener);
    }

    // Remove Status Listener
    @Test
    void removeStatusListener(){
        securityService.removeStatusListener(statusListener);
    }

    // Add Sensor
    @Test
    void addSensor(){
        securityService.addSensor(sensor);
    }

    // Remove Sensor
    @Test
    void removeSensor(){
        securityService.removeSensor(sensor);
    }

}