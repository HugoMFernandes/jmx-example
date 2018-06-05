import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Main {

    /**
     * Example using PID, but could use anything accessible by {@link VirtualMachineDescriptor}.
     * <p>
     * Replace with your PID.
     */
    private static final int JVM_PID = 8015;

    public static void main(String[] args) {

        // Get VMDs list and filter by PID
        List<VirtualMachineDescriptor> vmds = VirtualMachine.list();

        Optional<VirtualMachineDescriptor> vmdOpt = vmds.stream()
                .filter(vmd -> vmd.id().equals(String.valueOf(JVM_PID)))
                .findFirst();

        if (!vmdOpt.isPresent()) {
            System.err.printf("VM with PID %d not found.", JVM_PID);
            System.exit(1);
        }

        VirtualMachineDescriptor vmd = vmdOpt.get();

        try {
            // Attach to VM using VMD to load the agent and get the connector address (would have to redo if the JVM restarted)
            VirtualMachine vm = VirtualMachine.attach(vmd);
            String localConnectorAddress = vm.startLocalManagementAgent();
            vm.detach();

            // Build the service URL from the localConnectorAddress and connect to it
            JMXServiceURL jmxServiceURL = new JMXServiceURL(localConnectorAddress);
            JMXConnector connector = JMXConnectorFactory.connect(jmxServiceURL);

            // Get an MBeanServerConnection from the connector so we can browse MBeans
            MBeanServerConnection serverConnection = connector.getMBeanServerConnection();

            // You can access all the MBeans/attributes through the connection
            Set<ObjectInstance> exposedMBeans = serverConnection.queryMBeans(null, null);
            exposedMBeans.stream()
                    .forEach(mb -> printMBeanInfo(mb, serverConnection));

            connector.close();
        } catch (AttachNotSupportedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void printMBeanInfo(ObjectInstance objectInstance, MBeanServerConnection serverConnection) {
        try {
            // Attributes are accessible through the connection as well
            MBeanAttributeInfo[] attributes = serverConnection.getMBeanInfo(objectInstance.getObjectName()).getAttributes();

            List<String> readableAttributes = new ArrayList<>();
            for (MBeanAttributeInfo attr : attributes) {
                if (attr.isReadable()) {
                    readableAttributes.add(attr.getName());
                }
            }

            System.out.println(objectInstance.getObjectName() + " attributes:");

            // Get readable attributes through the connection
            String[] readableAttributesArr = new String[readableAttributes.size()];
            AttributeList attributeList = serverConnection.getAttributes(objectInstance.getObjectName(), readableAttributes.toArray(readableAttributesArr));
            attributeList.asList().stream()
                    .forEach(attr -> printAttribute(attr));
        } catch (InstanceNotFoundException e) {
            e.printStackTrace();
        } catch (IntrospectionException e) {
            e.printStackTrace();
        } catch (ReflectionException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void printAttribute(Attribute attribute) {
        Object value = attribute.getValue();

        if (value instanceof CompositeData) {
            printCompositeData((CompositeData) value);
        } else {
            System.out.println(attribute.getName() + ": " + value);
        }
    }

    private static void printCompositeData(CompositeData cd) {
        // CompositeData can be parsed into simple types all the way down from here and printed accordingly
        System.out.println(cd);
    }
}
