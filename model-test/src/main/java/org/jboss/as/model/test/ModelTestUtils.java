/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.model.test;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ATTRIBUTES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.w3c.dom.Document;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSParser;
import org.w3c.dom.ls.LSSerializer;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ModelTestUtils {
    /**
     * Read the classpath resource with the given name and return its contents as a string. Hook to
     * for reading in classpath resources for subsequent parsing. The resource is loaded using similar
     * semantics to {@link Class#getResource(String)}
     *
     * @param name the name of the resource
     * @return the contents of the resource as a string
     * @throws IOException
     */
    public static String readResource(final Class<?> clazz, final String name) throws IOException {

        URL configURL = clazz.getResource(name);
        Assert.assertNotNull(name + " url is null", configURL);

        BufferedReader reader = new BufferedReader(new InputStreamReader(configURL.openStream()));
        StringWriter writer = new StringWriter();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
            }
        } finally {
            reader.close();
        }
        return writer.toString();
    }

    /**
     * Checks that the result was successful and gets the real result contents
     *
     * @param result the result to check
     * @return the result contents
     */
    public static ModelNode checkResultAndGetContents(ModelNode result) {
        checkOutcome(result);
        Assert.assertTrue(result.hasDefined(RESULT));
        return result.get(RESULT);
    }

    /**
     * Checks that the result was successful
     *
     * @param result the result to check
     * @return the result contents
     */
    public static ModelNode checkOutcome(ModelNode result) {
        boolean success = SUCCESS.equals(result.get(OUTCOME).asString());
        Assert.assertTrue(result.get(FAILURE_DESCRIPTION).asString(), success);
        return result;
    }

    public static void validateModelDescriptions(PathAddress address, ImmutableManagementResourceRegistration reg) {
        ModelNode attributes = reg.getModelDescription(PathAddress.EMPTY_ADDRESS).getModelDescription(Locale.getDefault()).get(ATTRIBUTES);
        Set<String> regAttributeNames = reg.getAttributeNames(PathAddress.EMPTY_ADDRESS);
        Set<String> attributeNames = new HashSet<String>();
        if (attributes.isDefined()) {
            if (attributes.asList().size() != regAttributeNames.size()) {
                for (Property p : attributes.asPropertyList()) {
                    attributeNames.add(p.getName());
                }
                if (regAttributeNames.size() > attributeNames.size()) {
                    regAttributeNames.removeAll(attributeNames);
                    Assert.fail("More attributes defined on resource registration than in description, missing: " + regAttributeNames + " for " + address);
                } else if (regAttributeNames.size() < attributeNames.size()) {
                    attributeNames.removeAll(regAttributeNames);
                    Assert.fail("More attributes defined in description than on resource registration, missing: " + attributeNames + " for " + address);
                }
            }
            if (!attributeNames.containsAll(regAttributeNames)) {
                for (Property p : attributes.asPropertyList()) {
                    attributeNames.add(p.getName());
                }
                Set<String> missDesc = new HashSet<String>(attributeNames);
                missDesc.removeAll(regAttributeNames);

                Set<String> missReg = new HashSet<String>(regAttributeNames);
                missReg.removeAll(attributeNames);

                if (!missReg.isEmpty()) {
                    Assert.fail("There are different attributes defined on resource registration than in description, registered only on Resource Reg: " + missReg + " for " + address);
                }
                if (!missDesc.isEmpty()) {
                    Assert.fail("There are different attributes defined on resource registration than in description, registered only int description: " + missDesc + " for " + address);
                }
            }
        }
        for (PathElement pe : reg.getChildAddresses(PathAddress.EMPTY_ADDRESS)) {
            ImmutableManagementResourceRegistration sub = reg.getSubModel(PathAddress.pathAddress(pe));
            validateModelDescriptions(address.append(pe), sub);
        }
    }

    /**
     * Compares two models to make sure that they are the same
     *
     * @param node1 the first model
     * @param node2 the second model
     * @throws AssertionFailedError if the models were not the same
     */
    public static void compare(ModelNode node1, ModelNode node2) {
        compare(node1, node2, false);
    }

    /**
     * Compares two models to make sure that they are the same
     *
     * @param node1           the first model
     * @param node2           the second model
     * @param ignoreUndefined {@code true} if keys containing undefined nodes should be ignored
     * @throws AssertionFailedError if the models were not the same
     */
    public static void compare(ModelNode node1, ModelNode node2, boolean ignoreUndefined) {
        compare(node1, node2, ignoreUndefined, new Stack<String>());
    }

    /**
     * Normalize and pretty-print XML so that it can be compared using string
     * compare. The following code does the following: - Removes comments -
     * Makes sure attributes are ordered consistently - Trims every element -
     * Pretty print the document
     *
     * @param xml The XML to be normalized
     * @return The equivalent XML, but now normalized
     */
    public static String normalizeXML(String xml) throws Exception {
        // Remove all white space adjoining tags ("trim all elements")
        xml = xml.replaceAll("\\s*<", "<");
        xml = xml.replaceAll(">\\s*", ">");

        DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
        DOMImplementationLS domLS = (DOMImplementationLS) registry.getDOMImplementation("LS");
        LSParser lsParser = domLS.createLSParser(DOMImplementationLS.MODE_SYNCHRONOUS, null);

        LSInput input = domLS.createLSInput();
        input.setStringData(xml);
        Document document = lsParser.parse(input);

        LSSerializer lsSerializer = domLS.createLSSerializer();
        lsSerializer.getDomConfig().setParameter("comments", Boolean.FALSE);
        lsSerializer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);
        return lsSerializer.writeToString(document);
    }

    /**
     * Validate the marshalled xml without adjusting the namespaces for the original and marshalled xml.
     *
     * @param configId   the id of the xml configuration
     * @param original   the original subsystem xml
     * @param marshalled the marshalled subsystem xml
     * @throws Exception
     */
    public static void compareXml(final String original, final String marshalled) throws Exception {
        compareXml(original, marshalled, false);
    }

    /**
     * Validate the marshalled xml without adjusting the namespaces for the original and marshalled xml.
     *
     * @param configId        TODO
     * @param original        the original subsystem xml
     * @param marshalled      the marshalled subsystem xml
     * @param ignoreNamespace if {@code true} the subsystem's namespace is ignored, otherwise it is taken into account when comparing the normalized xml.
     * @throws Exception
     */
    public static void compareXml(final String original, final String marshalled, final boolean ignoreNamespace) throws Exception {
        final String xmlOriginal;
        final String xmlMarshalled;
        if (ignoreNamespace) {
            xmlOriginal = removeNamespace(original);
            xmlMarshalled = removeNamespace(marshalled);
        } else {
            xmlOriginal = original;
            xmlMarshalled = marshalled;
        }


        Assert.assertEquals(normalizeXML(xmlOriginal), normalizeXML(xmlMarshalled));
    }


    private static String removeNamespace(String xml) {
        int start = xml.indexOf(" xmlns=\"");
        int end = xml.indexOf('"', start + "xmlns=\"".length() + 1);
        if (start != -1) {
            StringBuilder sb = new StringBuilder(xml.substring(0, start));
            sb.append(xml.substring(end + 1));
            return sb.toString();
        }
        return xml;
    }


    private static void compare(ModelNode node1, ModelNode node2, boolean ignoreUndefined, Stack<String> stack) {
        Assert.assertEquals(getCompareStackAsString(stack) + " types", node1.getType(), node2.getType());
        if (node1.getType() == ModelType.OBJECT) {
            ModelNode model1 = ignoreUndefined ? trimUndefinedChildren(node1) : node1;
            ModelNode model2 = ignoreUndefined ? trimUndefinedChildren(node2) : node2;
            final Set<String> keys1 = new TreeSet<String>(model1.keys());
            final Set<String> keys2 = new TreeSet<String>(model2.keys());

            // compare string representations of the keys to help see the difference
            Assert.assertEquals(node1 + "\n" + node2, keys1.toString(), keys2.toString());
            Assert.assertTrue(keys1.containsAll(keys2));

            for (String key : keys1) {
                final ModelNode child1 = model1.get(key);
                Assert.assertTrue("Missing: " + key + "\n" + node1 + "\n" + node2, model2.has(key));
                final ModelNode child2 = model2.get(key);

                if (child1.isDefined()) {
                    if (!ignoreUndefined) {
                        Assert.assertTrue("key=" + key + "\n with child1 \n" + child1.toString() + "\n has child2 not defined\n node2 is:\n" + node2.toString(), child2.isDefined());
                    }
                    stack.push(key + "/");
                    compare(child1, child2, ignoreUndefined);
                    stack.pop();
                } else if (!ignoreUndefined) {
                    Assert.assertFalse(child2.asString(), child2.isDefined());
                }
            }
        } else if (node1.getType() == ModelType.LIST) {
            List<ModelNode> list1 = node1.asList();
            List<ModelNode> list2 = node2.asList();
            Assert.assertEquals(list1 + "\n" + list2, list1.size(), list2.size());

            for (int i = 0; i < list1.size(); i++) {
                stack.push(i + "/");
                compare(list1.get(i), list2.get(i), ignoreUndefined);
                stack.pop();
            }

        } else if (node1.getType() == ModelType.PROPERTY) {
            Property prop1 = node1.asProperty();
            Property prop2 = node2.asProperty();
            Assert.assertEquals(prop1 + "\n" + prop2, prop1.getName(), prop2.getName());
            stack.push(prop1.getName() + "/");
            compare(prop1.getValue(), prop2.getValue(), ignoreUndefined);
            stack.pop();

        } else {
            try {
                Assert.assertEquals(getCompareStackAsString(stack) +
                        "\n\"" + node1.asString() + "\"\n\"" + node2.asString() + "\"\n-----", node2.asString().trim(), node1.asString().trim());
            } catch (AssertionFailedError error) {
                throw error;
            }
        }
    }

    private static ModelNode trimUndefinedChildren(ModelNode model) {
        ModelNode copy = model.clone();
        for (String key : new HashSet<String>(copy.keys())) {
            if (!copy.hasDefined(key)) {
                copy.remove(key);
            } else if (copy.get(key).getType() == ModelType.OBJECT) {
                boolean undefined = true;
                for (ModelNode mn : model.get(key).asList()) {
                    Property p = mn.asProperty();
                    if (p.getValue().getType() != ModelType.OBJECT) { continue; }
                    for (String subKey : new HashSet<String>(p.getValue().keys())) {
                        if (copy.get(key, p.getName()).hasDefined(subKey)) {
                            undefined = false;
                            break;
                        } else {
                            copy.get(key, p.getName()).remove(subKey);
                        }
                    }
                    if (undefined) {
                        copy.get(key).remove(p.getName());
                        if (!copy.hasDefined(key)) {
                            copy.remove(key);
                        } else if (copy.get(key).getType() == ModelType.OBJECT) {     //this is stupid workaround
                            if (copy.get(key).keys().size() == 0) {
                                copy.remove(key);
                            }
                        }
                    }
                }
            }
        }
        return copy;
    }

    private static String getCompareStackAsString(Stack<String> stack) {
        String result = "";
        for (String element : stack) {
            result += element;
        }
        return result;
    }

}
