package org.freeshr.application.fhir;

import ca.uhn.fhir.context.FhirContext;
import org.apache.commons.lang3.StringUtils;
import org.freeshr.config.SHRProperties;
import org.freeshr.infrastructure.tr.TerminologyServer;
import org.hl7.fhir.dstu3.hapi.validation.IValidationSupport;
import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.Enumerations;
import org.hl7.fhir.dstu3.model.StructureDefinition;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.utilities.validation.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;


@Component
public class TRConceptValidator implements IValidationSupport {

    private final TerminologyServer terminologyServer;
    private SHRProperties shrProperties;
    private final static Logger logger = LoggerFactory.getLogger(TRConceptValidator.class);
    private final HashMap<String, String> map;

    @Autowired
    public TRConceptValidator(TerminologyServer terminologyServer, SHRProperties shrProperties) {
        this.terminologyServer = terminologyServer;
        this.shrProperties = shrProperties;
        map = new HashMap<>();
        loadValueSetUrls();
    }

    private void loadValueSetUrls() {
        map.put("http://hl7.org/fhir/ValueSet/v3-FamilyMember", shrProperties.getTRLocationPath() + "/openmrs/ws/rest/v1/tr/vs/Relationship-Type");
    }

    @Override
    public ValueSet.ValueSetExpansionComponent expandValueSet(FhirContext theContext, ValueSet.ConceptSetComponent theInclude) {
        return null;
    }

    @Override
    public List<StructureDefinition> fetchAllStructureDefinitions(FhirContext theContext) {
        return Collections.emptyList();
    }

    @Override
    public CodeSystem fetchCodeSystem(FhirContext theContext, String theSystem) {
        return null;
    }

    @Override
    @Cacheable(value = "shrProfileCache", unless = "#result == null")
    public <T extends IBaseResource> T fetchResource(FhirContext theContext, Class<T> theClass, String theUri) {
        IBaseResource baseResource = null;
        if (map.containsKey(theUri)) {
            String theSystem = map.get(theUri);
            ValueSet valueSet = new ValueSet();
            valueSet.setUrl(theUri);
            valueSet.setStatus(Enumerations.PublicationStatus.DRAFT);
            ValueSet.ValueSetComposeComponent valueSetComposeComponent = new ValueSet.ValueSetComposeComponent();
            valueSetComposeComponent.addInclude().setSystem(theSystem);
            valueSet.setCompose(valueSetComposeComponent);
            baseResource = valueSet;
        }
        return (T) baseResource;
    }

    @Override
    public StructureDefinition fetchStructureDefinition(FhirContext theCtx, String theUrl) {
        return null;
    }


    @Override
    public boolean isCodeSystemSupported(FhirContext theContext, String theSystem) {
        return terminologyServer.verifiesSystem(StringUtils.trim(theSystem));
    }

    @Override
    @Cacheable(value = "trCache", unless = "#result.ok == false")
    public CodeValidationResult validateCode(FhirContext theContext, String theCodeSystem, String theCode, String theDisplay) {
        /** TODO - Note: should we be creating a custom CodeValidationResult and return that?
         * the caching expression "unless" uses the javabeans convention for boolean property for ok (isOk) rather than a field
         */
        CodeValidationResult result = locate(theCodeSystem, theCode, theDisplay);
        return result;
    }

    private CodeValidationResult locate(String system, String code, String display) {
        try {
            Boolean result = terminologyServer.isValid(system, code).toBlocking().first();
            if (result != null && result.booleanValue()) {
                CodeSystem.ConceptDefinitionComponent def = new CodeSystem.ConceptDefinitionComponent();
                def.setDefinition(system);
                def.setCode(code);
                def.setDisplay(display);
                return new CodeValidationResult(def);
            } else {
                return new CodeValidationResult(ValidationMessage.IssueSeverity.ERROR, String.format("Could not validate concept system[%s], code[%s]", system, code));
            }
        } catch (Exception e) {
            logger.error(String.format("Problem while validating concept system[%s], code[%s]", system, code), e);
            return new CodeValidationResult(ValidationMessage.IssueSeverity.ERROR, "Couldn't identify system and code. error:" + e.getMessage());
        }
    }
}
