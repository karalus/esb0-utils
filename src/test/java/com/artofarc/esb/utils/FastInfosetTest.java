package com.artofarc.esb.utils;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;

import com.artofarc.esb.AbstractESBTest;
import com.artofarc.esb.ConsumerPort;
import com.artofarc.esb.action.Action;
import com.artofarc.esb.action.DumpAction;
import com.artofarc.esb.action.SetMessageAction;
import com.artofarc.esb.action.UnwrapSOAPAction;
import com.artofarc.esb.action.WrapSOAPAction;
import com.artofarc.esb.artifact.FileSystem;
import com.artofarc.esb.artifact.WSDLArtifact;
import com.artofarc.esb.http.HttpConstants;
import com.artofarc.esb.message.BodyType;
import com.artofarc.esb.message.ESBMessage;
import com.artofarc.esb.utils.fastinfoset.FIDeserializerSetVocabularyAction;
import com.artofarc.esb.utils.fastinfoset.FISerializerSetVocabularyAction;
import com.artofarc.util.WSDL4JUtil;

public class FastInfosetTest extends AbstractESBTest {

	@Before
	public void createContext() throws Exception {
		createContext(new File("src/test/resources"));
	}

	@Test
	public void testFastInfoset() throws Exception {
		FileSystem fileSystem = getGlobalContext().getFileSystem();
		fileSystem.init(getGlobalContext()).getServiceArtifacts();
		WSDLArtifact wsdlArtifact = fileSystem.getArtifact("/example/example.wsdl");
		assertNotNull(wsdlArtifact);
		wsdlArtifact.validate(getGlobalContext());

		ESBMessage message = new ESBMessage(BodyType.BYTES, readFile("src/test/resources/SOAPRequest.xml"));
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, "text/xml; charset=\"utf-8\"");
		message.putHeader(HttpConstants.HTTP_HEADER_SOAP_ACTION, "\"\"");

		@SuppressWarnings("unchecked")
		Action action = new UnwrapSOAPAction(false, true, wsdlArtifact.getSchema(), WSDL4JUtil.getBinding(wsdlArtifact.getAllBindings(), null, null).getBindingOperations(), null, false);
		ConsumerPort consumerPort = new ConsumerPort(null);
		consumerPort.setStartAction(action);
		action = action.setNextAction(new WrapSOAPAction(false, false, true));
		SetMessageAction setMessageAction = new SetMessageAction(null, null, null, null);
		setMessageAction.addAssignment(HttpConstants.HTTP_HEADER_CONTENT_TYPE, true, HttpConstants.HTTP_HEADER_CONTENT_TYPE_FI_SOAP11, null, null, null);
		action = action.setNextAction(setMessageAction);
		action = action.setNextAction(new DumpAction());
		action = action.setNextAction(createUnwrapSOAPAction(false, true));
		action = action.setNextAction(new WrapSOAPAction(false, false, true));
		action = action.setNextAction(new DumpAction());
		consumerPort.process(context, message);
	}

	@Test
	public void testFastInfosetVocabulary() throws Exception {
		FileSystem fileSystem = getGlobalContext().getFileSystem();
		fileSystem.init(getGlobalContext()).getServiceArtifacts();
		WSDLArtifact wsdlArtifact = fileSystem.getArtifact("/example/example.wsdl");
		assertNotNull(wsdlArtifact);
		wsdlArtifact.validate(getGlobalContext());

		ESBMessage message = new ESBMessage(BodyType.BYTES, readFile("src/test/resources/SOAPRequest.xml"));
		message.putHeader(HttpConstants.HTTP_HEADER_CONTENT_TYPE, "text/xml; charset=\"utf-8\"");
		message.putHeader(HttpConstants.HTTP_HEADER_SOAP_ACTION, "\"\"");

		Properties properties = new Properties();
		properties.setProperty("schemaArtifactURI", "/example/example.wsdl");
		properties.setProperty("ignoreWhitespace", "true");
		properties.setProperty("beautify", "true");

		@SuppressWarnings("unchecked")
		Action action = new UnwrapSOAPAction(false, true, wsdlArtifact.getSchema(), WSDL4JUtil.getBinding(wsdlArtifact.getAllBindings(), null, null).getBindingOperations(), null, false);
		ConsumerPort consumerPort = new ConsumerPort(null);
		consumerPort.setStartAction(action);
		action = action.setNextAction(new WrapSOAPAction(false, false, true));
		SetMessageAction setMessageAction = new SetMessageAction(null, null, null, null);
		setMessageAction.addAssignment(HttpConstants.HTTP_HEADER_CONTENT_TYPE, true, HttpConstants.HTTP_HEADER_CONTENT_TYPE_FI_SOAP11, null, null, null);
		action = action.setNextAction(setMessageAction);
		action = action.setNextAction(new FISerializerSetVocabularyAction(getGlobalContext().getClassLoader(), properties));
		action = action.setNextAction(new DumpAction());
		action = action.setNextAction(new FIDeserializerSetVocabularyAction(getGlobalContext().getClassLoader(), properties));
		action = action.setNextAction(createUnwrapSOAPAction(false, true));
		action = action.setNextAction(new WrapSOAPAction(false, false, true));
		action = action.setNextAction(new DumpAction());
		consumerPort.process(context, message);
	}

}
