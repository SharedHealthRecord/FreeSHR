package org.freeshr.validations.resource;


import ca.uhn.fhir.model.api.IDatatype;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.QuantityDt;
import ca.uhn.fhir.validation.IValidationSupport;
import org.freeshr.application.fhir.TRConceptValidator;
import org.freeshr.validations.Severity;
import org.freeshr.validations.ShrValidationMessage;
import org.freeshr.validations.SubResourceValidator;
import org.freeshr.validations.UrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.freeshr.validations.ValidationMessages.*;

@Component
public class MedicationOrderValidator implements SubResourceValidator {

    private static final Logger logger = LoggerFactory.getLogger(MedicationOrderValidator.class);
    public static final String MEDICATION_ORDER_MEDICATION_LOCATION = "f:MedicationOrder/f:medication";
    public static final String MEDICATION_DOSE_INSTRUCTION_LOCATION = "f:MedicationOrder/f:dosageInstruction/f:dose";
    private String MEDICATION_ORDER_DISPENSE_MEDICATION_LOCATION = "f:MedicationOrder/f:dispenseRequest/f:medication";
    private TRConceptValidator trConceptValidator;
    private DoseQuantityValidator doseQuantityValidator;
    private UrlValidator urlValidator;

    @Autowired
    public MedicationOrderValidator(TRConceptValidator trConceptValidator,
                                    DoseQuantityValidator doseQuantityValidator, UrlValidator urlValidator) {
        this.trConceptValidator = trConceptValidator;
        this.doseQuantityValidator = doseQuantityValidator;
        this.urlValidator = urlValidator;
    }

    @Override
    public boolean validates(Object resource) {
        return resource instanceof ca.uhn.fhir.model.dstu2.resource.MedicationOrder;
    }

    @Override
    public List<ShrValidationMessage> validate(Object resource) {
        ca.uhn.fhir.model.dstu2.resource.MedicationOrder medicationOrder = (ca.uhn.fhir.model.dstu2.resource.MedicationOrder) resource;
        List<ShrValidationMessage> validationMessages = new ArrayList<>();

        validationMessages.addAll(validateMedication(medicationOrder));
        if (validationMessages.size() > 0) return validationMessages;

        validationMessages.addAll(validateDosageQuantity(medicationOrder));
        if (validationMessages.size() > 0) return validationMessages;

        validationMessages.addAll(validateDispenseMedication(medicationOrder));
        return validationMessages;
    }

    private Collection<? extends ShrValidationMessage> validateDispenseMedication(ca.uhn.fhir.model.dstu2.resource.MedicationOrder medicationOrder) {
        if(medicationOrder.getDispenseRequest() != null) {
            IDatatype medicine = medicationOrder.getDispenseRequest().getMedication();
            if (medicine == null || !(medicine instanceof CodeableConceptDt)) {
                return new ArrayList<>();
            }

            CodeableConceptDt medicationCoding = ((CodeableConceptDt) medicine);

            return validateCodeableConcept(medicationCoding, MEDICATION_ORDER_DISPENSE_MEDICATION_LOCATION, INVALID_DISPENSE_MEDICATION_REFERENCE_URL);
        }
        return new ArrayList<>();
    }

    private Collection<? extends ShrValidationMessage> validateDosageQuantity(ca.uhn.fhir.model.dstu2.resource.MedicationOrder medicationOrder) {
        List<ca.uhn.fhir.model.dstu2.resource.MedicationOrder.DosageInstruction> instructions = medicationOrder.getDosageInstruction();


        for (ca.uhn.fhir.model.dstu2.resource.MedicationOrder.DosageInstruction instruction : instructions) {
            IDatatype dose = instruction.getDose();

            if (dose instanceof QuantityDt) {
                QuantityDt doseQuantity = (QuantityDt) dose;
                if (doseQuantityValidator.isReferenceUrlNotFound(doseQuantity)) return new ArrayList<>();

                if (!urlValidator.isValid(doseQuantity.getSystem())) {
                    logger.debug(String.format("Medication-Prescription:Encounter failed for %s", INVALID_DOSAGE_QUANTITY_REFERENCE));
                    return Arrays.asList(
                            new ShrValidationMessage(Severity.ERROR, MEDICATION_DOSE_INSTRUCTION_LOCATION, "invalid", INVALID_DOSAGE_QUANTITY_REFERENCE));
                }
                IValidationSupport.CodeValidationResult codeValidationResult = doseQuantityValidator.validate(doseQuantity);
                if (!codeValidationResult.isOk()) {
                    logger.debug(String.format("Medication-Prescription:Encounter failed for %s", codeValidationResult.getMessage()));
                    return Arrays.asList(
                            new ShrValidationMessage(Severity.ERROR, MEDICATION_DOSE_INSTRUCTION_LOCATION, "invalid", codeValidationResult.getMessage()));
                }
            }
        }
        return new ArrayList<>();
    }

    private Collection<? extends ShrValidationMessage> validateMedication(ca.uhn.fhir.model.dstu2.resource.MedicationOrder medicationOrder) {
        IDatatype medicine = medicationOrder.getMedication();
        if (!(medicine instanceof CodeableConceptDt)) {
            return new ArrayList<>();
        }

        CodeableConceptDt medicationCoding = ((CodeableConceptDt) medicine);
        if (medicationCoding.isEmpty()) {
            return Arrays.asList(new ShrValidationMessage(Severity.ERROR, MEDICATION_ORDER_MEDICATION_LOCATION, "invalid",
                    UNSPECIFIED_MEDICATION));
        }

        return validateCodeableConcept(medicationCoding, MEDICATION_ORDER_MEDICATION_LOCATION, INVALID_MEDICATION_REFERENCE_URL);
    }

    private Collection<? extends ShrValidationMessage> validateCodeableConcept(CodeableConceptDt medicationCoding, String location, String message) {
        ArrayList<ShrValidationMessage> shrValidationMessages = new ArrayList<>();
        for (CodingDt codingDt : medicationCoding.getCoding()) {
            if (codingDt.getSystem() != null && codingDt.getCode() != null) {
                if (trConceptValidator.isCodeSystemSupported(codingDt.getSystem())) {
                    IValidationSupport.CodeValidationResult validationResult = trConceptValidator.validateCode(codingDt.getSystem(), codingDt.getCode(), codingDt.getDisplay());
                    if (validationResult != null && !validationResult.isOk()) {
                        logger.debug(String.format("Medication-Order:Encounter failed for %s", message));
                        shrValidationMessages.add(new ShrValidationMessage(Severity.ERROR, location, "invalid", message));
                    }
                }
            }
        }
        return shrValidationMessages;
    }
}
