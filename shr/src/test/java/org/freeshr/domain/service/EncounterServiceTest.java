package org.freeshr.domain.service;


import org.freeshr.application.fhir.EncounterBundle;
import org.freeshr.application.fhir.FhirValidator;
import org.freeshr.domain.model.Facility;
import org.freeshr.domain.model.patient.Address;
import org.freeshr.infrastructure.persistence.EncounterRepository;
import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EncounterServiceTest {

    FacilityService facilityService;
    EncounterService encounterService;
    EncounterRepository encounterRepository;

    @Before
    public void setup(){
        encounterRepository = mock(EncounterRepository.class);
        facilityService = mock(FacilityService.class);
        encounterService = new EncounterService(encounterRepository, mock(PatientService.class), mock(FhirValidator.class), facilityService);
    }


    @Test
    public void shouldReturnErrorEvenIfOneGetEncounterFails() throws ExecutionException, InterruptedException, ParseException {
        String date = "2014-09-10";
        when(facilityService.ensurePresent("1")).thenReturn(new Facility("1", "facility1", "Main hospital", "3056,30", new Address("1", "2", "3", null, null)));

        final String exceptionMessage = "I bombed";

        when(encounterRepository.findEncountersForCatchment(eq(new FacilityCatchment("30")),  org.mockito.Matchers.any(Date.class), eq(20))).thenThrow(new ExecutionException(exceptionMessage, null));

        try {
            //encounterRepository.findEncountersForCatchment(facilityCatchment, updateSince, DEFAULT_FETCH_LIMIT)
            List<EncounterBundle> encountersByCatchments = encounterService.findAllEncountersByFacilityCatchments("1", date);
        } catch (Exception e) {
            assertEquals(e.getMessage(), exceptionMessage);
        }
    }
}
