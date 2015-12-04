/**
 *  Soy <3 SmartThings
 *
 *  Copyright 2015 Soy Chat
 *
 */
definition(
    name: "Soy <3 SmartThings",
    namespace: "hfwang",
    author: "Soy Chat",
    description: "Exposes a smartthings hub to the vagries of soy chat.",
    category: "SmartThings Labs",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Allow Soy to control these things...") {
		input "switches", "capability.switch", title: "Which Switches?", multiple: true, required: false
		input "motionSensors", "capability.motionSensor", title: "Which Motion Sensors?", multiple: true, required: false
		input "contactSensors", "capability.contactSensor", title: "Which Contact Sensors?", multiple: true, required: false
		input "presenceSensors", "capability.presenceSensor", title: "Which Presence Sensors?", multiple: true, required: false
		input "temperatureSensors", "capability.temperatureMeasurement", title: "Which Temperature Sensors?", multiple: true, required: false
		input "accelerationSensors", "capability.accelerationSensor", title: "Which Vibration Sensors?", multiple: true, required: false
		input "waterSensors", "capability.waterSensor", title: "Which Water Sensors?", multiple: true, required: false
		input "lightSensors", "capability.illuminanceMeasurement", title: "Which Light Sensors?", multiple: true, required: false
		input "humiditySensors", "capability.relativeHumidityMeasurement", title: "Which Relative Humidity Sensors?", multiple: true, required: false
		input "alarms", "capability.alarm", title: "Which Sirens?", multiple: true, required: false
		input "locks", "capability.lock", title: "Which Locks?", multiple: true, required: false
	}
}

mappings {
	path("/:deviceType") {
		action: [
			GET: "list"
		]
	}
	path("/:deviceType/states") {
		action: [
			GET: "listStates"
		]
	}
	path("/:deviceType/subscription") {
		action: [
			POST: "addSubscription"
		]
	}
	path("/:deviceType/subscriptions/:id") {
		action: [
			DELETE: "removeSubscription"
		]
	}
	path("/:deviceType/states/:id") {
		action: [
			GET: "listDeviceStates"
		]
	}
	path("/:deviceType/events/:id") {
		action: [
			GET: "listDeviceEvents"
		]
	}
	path("/:deviceType/:id") {
		action: [
			GET: "show",
			PUT: "update"
		]
	}
	path("/subscriptions") {
		action: [
			GET: "listSubscriptions"
		]
	}
}

def installed() {
	log.debug settings
}

def updated() {
	log.debug settings
}

def list() {
	log.debug "[PROD] list, params: ${params}"
	def type = params.deviceType
	settings[type]?.collect{deviceItem(it)} ?: []
}

def listStates() {
	log.debug "[PROD] states, params: ${params}"
	def type = params.deviceType
	def attributeName = attributeFor(type)
	settings[type]?.collect{deviceState(it, it.currentState(attributeName))} ?: []
}

def listDeviceStates() {
	log.debug "[PROD] deviceStates, params: ${params}"
	log.debug "[PROD] deviceStates, param[:id]: ${params.id}"
	def type = params.deviceType
	def devices = settings[type]
    def device = devices?.find { it.id == params.id }
	log.debug "[PROD] deviceStates, device: ${device}"
	def attributeName = attributeFor(type)
    device.statesSince(attributeName, new Date(0)).collect { deviceState(device, it) }
}

def listDeviceEvents() {
	log.debug "[PROD] eventStates, params: ${params}"
	def type = params.deviceType
	def devices = settings[type]
    def device = devices?.find { it.id == params.id }
	def attributeName = attributeFor(type)
    device.events()?.collect { deviceEvent(it) }
}

def listSubscriptions() {
	state
}

def update() {
	def type = params.deviceType
	def data = request.JSON
	def devices = settings[type]
	def command = data.command

	log.debug "[PROD] update, params: ${params}, request: ${data}, devices: ${devices*.id}"
	if (command) {
		def device = devices?.find { it.id == params.id }
		if (!device) {
			httpError(404, "Device not found")
		} else {
			device."$command"()
		}
	}
}

def show() {
	def type = params.deviceType
	def devices = settings[type]
	def device = devices.find { it.id == params.id }
	log.debug "[PROD] deviceStates, device: ${device}"

	log.debug "[PROD] show, params: ${params}, devices: ${devices*.id}"
	if (!device) {
		httpError(404, "Device not found")
	}
	else {
		def attributeName = attributeFor(type)
		def s = device.currentState(attributeName)
		deviceState(device, s)
	}
}

def addSubscription() {
	log.debug "[PROD] addSubscription1"
	def type = params.deviceType
	def data = request.JSON
	def attribute = attributeFor(type)
	def devices = settings[type]
	def deviceId = data.deviceId
	def callbackUrl = data.callbackUrl
	def device = devices.find { it.id == deviceId }

	log.debug "[PROD] addSubscription, params: ${params}, request: ${data}, device: ${device}"
	if (device) {
		log.debug "Adding switch subscription " + callbackUrl
		state[deviceId] = [callbackUrl: callbackUrl]
		subscribe(device, attribute, deviceHandler)
	}
	log.info state

}

def removeSubscription() {
	def type = params.deviceType
	def devices = settings[type]
	def deviceId = params.id
	def device = devices.find { it.id == deviceId }

	log.debug "[PROD] removeSubscription, params: ${params}, request: ${data}, device: ${device}"
	if (device) {
		log.debug "Removing $device.displayName subscription"
		state.remove(device.id)
		unsubscribe(device)
	}
	log.info state
}

def deviceHandler(evt) {
	def deviceInfo = state[evt.deviceId]
	if (deviceInfo) {
		try {
			httpPostJson(uri: deviceInfo.callbackUrl, path: '',  body: [evt: deviceEvent(evt)]) {
				log.debug "[PROD IFTTT] Event data successfully posted"
			}
		} catch (groovyx.net.http.ResponseParseException e) {
			log.debug("Error parsing ifttt payload ${e}")
		}
	} else {
		log.debug "[PROD] No subscribed device found"
	}
}

private deviceItem(it) {
	it ? [id: it.id, label: it.displayName] : null
}

private deviceState(device, s) {
	device && s ? [id: device.id, label: device.displayName, name: s.name, value: s.value, unixTime: s.date.time] : null
}

private deviceEvent(evt) {
    [id: evt.id.toString(), deviceId: evt.deviceId, name: evt.name, value: evt.value, date: evt.isoDate, description: evt.description, descriptionText: evt.descriptionText, isStateChange: evt.isStateChange(), installedSmartAppId: evt.installedSmartAppId, source: evt.source]
}

private attributeFor(type) {
	switch (type) {
		case "switches":
			log.debug "[PROD] switch type"
			return "switch"
		case "locks":
			log.debug "[PROD] lock type"
			return "lock"
		case "alarms":
			log.debug "[PROD] alarm type"
			return "alarm"
		case "lightSensors":
			log.debug "[PROD] illuminance type"
			return "illuminance"
		default:
			log.debug "[PROD] other sensor type"
			return type - "Sensors"
	}
}