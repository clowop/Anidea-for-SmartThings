/*
 * ---------------------------------------------------------------------------------
 * (C) Graham Johnson (orangebucket)
 *
 * Permission to use, copy, modify, and/or distribute this software for any purpose
 * with or without fee is hereby granted, provided that the copyright notice below
 * and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH 
 * REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND 
 * FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 * INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS
 * OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER 
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
 * THIS SOFTWARE.
 * ---------------------------------------------------------------------------------
 *
 * HTTP Response Motion Sensor
 * ===========================
 * Version: 20.02.06.00
 *
 * This device handler implements a virtual motion sensor which is active when a
 * specified HTTP server on the hub's local network can be reached. Every fifteen 
 * minutes the status is checked and set to active if a response is received. If
 * no response is received within 60 seconds it is set to inactive.
 *
 * The capabilities supported are:
 *
 *		Actuator (*)
 *		Motion Sensor
 *		Refresh
 *		Sensor (*)
 *
 * (*) The Actuator and Sensor capabilities are shown as deprecated in the Capabilities
 * references, yet their use is also encouraged as best practice. The Actuator capability
 * just means the device has commands. The Sensor capability means it has attributes.
 *
 * Please be aware that this file is created in the SmartThings Groovy IDE and it may
 * format differently when viewed outside that environment.
 */

metadata
{
	definition (name: "HTTP Response Motion Sensor", namespace: "orangebucket", author: "Graham Johnson")
    {
		capability "Actuator"
        capability "Motion Sensor"
        capability "Refresh"
        capability "Sensor"
	}

	preferences
	{
    	input name: "ip", type: "text", title: "IP Address", description: "e.g. 192.168.1.2", required: true
    	input name: "port", type: "text", title: "Port", description: "e.g. 8000", required: true
	}

	// One day I will investigate this.
	simulator
    {
	}

	// UI.
    //
    // White (#ffffff) is the standard for 'off'-like states.
    // Blue (#00a0dc) is the standard for 'on'-like states.
    // Orange (#e86d13) is the standard for devices requiring attention.
    // Grey (#cccccc) is the standard for inactive or offline devices.
    //
	tiles
    {
		standardTile("motion", "device.motion", width: 2, height: 2)
        {
			state "active", label:'active', icon:"st.motion.motion.active", backgroundColor:"#00a0dc"
			state "inactive", label:'inactive', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
        }
        
 		standardTile("refresh", "device.motion", width: 1, height: 1)
        {
        	state "default", label:'refresh', action:"refresh.refresh", icon:"st.secondary.refresh", backgroundColor:"#ffffff"
		}
              
        main "motion"
        details (["motion", "refresh"])
	}
}

// Call the updated() command on device installation.
def installed()
{
	log.debug "${device}: installed"
    
	updated()
}

// Schedule a call to polldevice() every fifteen minutes and also call it in
// ten seconds time.
def updated()
{
	log.debug "${device}: updated"
 
	unschedule()
  
    runEvery15Minutes(polldevice)

	runIn(1, setdni)

	runIn(10, polldevice)
}

def setdni()
{
	def address = settings.ip
	def port = settings.port
	def octets = address.tokenize('.')
	def hex = ""

	octets.each
    {
		hex = hex + Integer.toHexString(it as Integer).toUpperCase().padLeft(2, '0')
	}

	hex = hex + ":" + Integer.toHexString(port as Integer).toUpperCase().padLeft(4, '0')

	if (device.getDeviceNetworkId() != hex)
    {
    	log.debug  "${device}: setdni ${settings.ip}:${settings.port} ${hex}"
  	  
		device.setDeviceNetworkId(hex)
    }
    else log.debug "${device}: setdni (not needed)"
}

// If parse() is called that suggests the remote device is switched on and
// so the motion sensor is set to to active.
def parse(description)
{
	def msg = parseLanMessage(description)

    log.debug "${device}: parse"
        
    state.waitingforresponse = false
    def stateevent = createEvent(name: 'motion', value: 'active')
        
    return stateevent
}

// SmartThings will automatically run a HubAction returned by a command such as 
// refresh(). However if refresh() is called from the scheduler it would not be
// acting as a command. It is therefore necessary to use a separate method for 
// scheduling that calls sendHubCommand() manually.

// Wrapper for refresh() for scheduling.
def polldevice()
{    
    log.debug "${device}: polldevice"
    
    sendHubCommand(refresh())
}

def refresh()
{    
    log.debug "${device}: refresh"
    
    runIn(60, responsecheck)
    
    state.waitingforresponse = true
    
    return buildhubaction()
}

def responsecheck()
{
    if (state.waitingforresponse)
    {   	
    	log.debug "${device}: responsecheck - no response so assumed inactive"
                
        sendEvent(name: 'motion', value: 'inactive')
    }
    else log.debug "${device}: responsecheck - response has been received"
}

def buildhubaction()
{
    def hex = device.getDeviceNetworkId()

	def hubaction = new physicalgraph.device.HubAction(
        method	: "GET",
        path	: "/",
        headers	:
            [
            	"HOST": "${hex}",
      		]
	)
    
	return hubaction
 }
