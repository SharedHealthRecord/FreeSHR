package org.freeshr.application.fhir;


import org.freeshr.config.SHRProperties;
import org.freeshr.utils.CollectionUtils;
import org.hl7.fhir.instance.model.OperationOutcome;
import org.hl7.fhir.instance.validation.InstanceValidator;
import org.hl7.fhir.instance.validation.ValidationMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static org.freeshr.utils.CollectionUtils.filter;
import static org.freeshr.utils.CollectionUtils.reduce;

@Component
public class FhirValidator {

    private TRConceptLocator trConceptLocator;
    private SHRProperties shrProperties;

    @Autowired
    public FhirValidator(TRConceptLocator trConceptLocator, SHRProperties shrProperties) {
        this.trConceptLocator = trConceptLocator;
        this.shrProperties = shrProperties;
    }

    public EncounterValidationResponse validate(String sourceXML) {
        try {
            return validate(sourceXML, shrProperties.getValidationFilePath());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private EncounterValidationResponse validate(String sourceXml, String definitionsZipPath) {
        List<ValidationMessage> outputs = new ArrayList<>();
        outputs.addAll(validateDocument(definitionsZipPath, sourceXml));
        return filterMessagesSevereThan(outputs, OperationOutcome.IssueSeverity.warning);
    }

    private List<ValidationMessage> validateDocument(String definitionsZipPath, String sourceXml) {
        try {
            return new InstanceValidator(definitionsZipPath, null, trConceptLocator).validateInstance(document(sourceXml).getDocumentElement());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Document document(String sourceXml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(sourceXml.getBytes()));
    }

    private EncounterValidationResponse filterMessagesSevereThan(List<ValidationMessage> outputs, final OperationOutcome.IssueSeverity severity) {
        return reduce(filter(outputs, new CollectionUtils.Fn<ValidationMessage, Boolean>() {
            @Override
            public Boolean call(ValidationMessage input) {
                return severity.compareTo(input.getLevel()) >= 0;
            }
        }), new EncounterValidationResponse(), new CollectionUtils.ReduceFn<ValidationMessage, EncounterValidationResponse>() {
            @Override
            public EncounterValidationResponse call(ValidationMessage input, EncounterValidationResponse acc) {
                Error error = new Error();
                error.setField(input.getLocation());
                error.setType(input.getType());
                error.setReason(input.getMessage());
                acc.addError(error);
                return acc;
            }
        });
    }
}