/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.asasvari.debug;

import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventIterator;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import java.util.logging.Logger;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ShellScriptLauncherDebugger {
    final String host;
    final String port;
    final String brkClass;
    final String brkMethod;
    final String shellCmd;

    private final static Logger LOGGER = Logger.getLogger(ShellScriptLauncherDebugger.class.getName());

    public ShellScriptLauncherDebugger(String host, String port, String brkClass, String brkMethod, String shellCmd) {
        this.host = host;
        this.port = port;
        this.brkClass = brkClass;
        this.brkMethod = brkMethod;
        this.shellCmd = shellCmd;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            LOGGER.severe("Specify arguments: \"host:port\" \"breakpoint_class.method\" \"shell_script\"");
            return;
        }
        final String host = args[0].split(":")[0];
        final String port = args[0].split(":")[1];

        final String brTarget = args[1];
        final String brkClass = brTarget.substring(0, brTarget.lastIndexOf("."));
        final String brkMethod = brTarget.substring(brTarget.lastIndexOf(".") + 1);
        final String shellCmd = args[2];

        final ShellScriptLauncherDebugger shellScriptLauncherDebugger = new ShellScriptLauncherDebugger(host,
                port, brkClass, brkMethod, shellCmd);
        shellScriptLauncherDebugger.run();
    }

    public void run() throws Exception {
        final VirtualMachine vm = attachToVirtualMachine();

        //listThreads(vm);
        Location breakpointLocation = findBreakPointLocation(vm);

        setBreakPoint(vm, breakpointLocation);

        processEvents(vm, breakpointLocation);
        return;
    }

    private void listThreads(VirtualMachine vm) throws IncompatibleThreadStateException {
        for (ThreadReference t : vm.allThreads()) {
            t.suspend();
            LOGGER.info("---- " + t.name() + " ----");
            for (StackFrame f : t.frames()) {
                LOGGER.info(f.toString());
            }
            t.resume();
        }
    }

    private VirtualMachine attachToVirtualMachine() throws IOException, IllegalConnectorArgumentsException {
        LOGGER.info("Attaching to " + host + ":" + port);
        final VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        AttachingConnector atconn = null;
        for (AttachingConnector attachingConnector : vmm.attachingConnectors()) {
            if ("dt_socket".equalsIgnoreCase(attachingConnector.transport().name())) {
                atconn = attachingConnector;
            }
        }
        final Map<String, Connector.Argument> prm = atconn.defaultArguments();
        prm.get("port").setValue(port);
        prm.get("hostname").setValue(host);
        return atconn.attach(prm);
    }

    private Location findBreakPointLocation(VirtualMachine vm) {
        LOGGER.info("Setting breakpoint at " + brkClass + "." + brkMethod);

        final List<ReferenceType> refTypes = vm.allClasses();
        Location breakpointLocation = null;

        for (ReferenceType refType: refTypes)
        {
            final String rName = refType.name();
            //System.out.println(".. " + rName);

            if (!rName.equals(brkClass)) {
                continue;
            }

            if (breakpointLocation != null)
            {
                break;
            }

            final List<Method> methods = refType.allMethods();
            for (Method m: methods)
            {
                String mName = m.name();
                if (mName.equals(brkMethod))
                {
                    breakpointLocation = m.location();
                    break;
                }
            }
        }
        return breakpointLocation;
    }

    private void setBreakPoint(VirtualMachine vm, Location breakpointLocation) {
        final EventRequestManager evRm = vm.eventRequestManager();
        final BreakpointRequest bReq = evRm.createBreakpointRequest(breakpointLocation);
        bReq.setSuspendPolicy(BreakpointRequest.SUSPEND_ALL);
        bReq.enable();
    }

    private void processEvents(VirtualMachine vm, Location breakpointLocation) throws InterruptedException {
        final EventQueue evtQueue = vm.eventQueue();
        while(true)
        {
            final EventSet evtSet = evtQueue.remove();
            final EventIterator evtIter = evtSet.eventIterator();

            while (evtIter.hasNext())
            {
                try
                {
                    final Event evt = evtIter.next();
                    final EventRequest evtReq = evt.request();
                    if (evtReq instanceof BreakpointRequest)
                    {
                        handleBreakPoint(breakpointLocation, (BreakpointEvent) evt, (BreakpointRequest) evtReq);
                    }
                }
                catch (AbsentInformationException aie)
                {
                    LOGGER.warning("AbsentInformationException: did you compile your target application with -g option?");
                }
                catch (Exception exc)
                {
                    LOGGER.warning(exc.getClass().getName() + ": " + exc.getMessage());
                }
                finally
                {
                    evtSet.resume();
                }
            }
        }
    }

    private void handleBreakPoint(Location breakpointLocation, BreakpointEvent evt, BreakpointRequest evtReq)
            throws IncompatibleThreadStateException, IOException, AbsentInformationException {
        final BreakpointRequest bpReq = evtReq;
        if (!bpReq.location().equals(breakpointLocation))
        {
            return;
        }
        LOGGER.info("Breakpoint hit at  " + breakpointLocation);
        Runtime.getRuntime().exec(shellCmd);

        final BreakpointEvent brEvt = evt;
        final ThreadReference threadRef = brEvt.thread();
        final StackFrame stackFrame = threadRef.frame(0);

        final List<LocalVariable> visVars = stackFrame.visibleVariables();
        for (LocalVariable visibleVar : visVars) {
            final Value val = stackFrame.getValue(visibleVar);
            if (val instanceof StringReference)
            {
                String varNameValue = ((StringReference)val).value();
                LOGGER.info(val + " = '" + varNameValue + "'");
            }
        }
    }
}