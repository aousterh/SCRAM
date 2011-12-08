/* Copyright 2008-2009 Uppsala University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#include <libcpphaggle/Platform.h>
#include "ProtocolManager.h"
#include "Protocol.h"
#include "ProtocolUDP.h"
#include "ProtocolTCP.h"
#if defined(OS_UNIX)
#include "ProtocolLOCAL.h"
#endif
#if defined(ENABLE_MEDIA)
#include "ProtocolMedia.h"
#endif
#if defined(ENABLE_BLUETOOTH)
#include "ProtocolRFCOMM.h"
#endif
#include <haggleutils.h>

ProtocolManager::ProtocolManager(HaggleKernel * _kernel) :
	Manager("ProtocolManager", _kernel), tcpServerPort(TCP_DEFAULT_PORT), 
	tcpBacklog(TCP_BACKLOG_SIZE), killer(NULL)
{	
}

ProtocolManager::~ProtocolManager()
{
	while (!protocol_registry.empty()) {
		Protocol *p = (*protocol_registry.begin()).second;
		protocol_registry.erase(p->getId());
		delete p;
	}
	
	if (killer) {
		killer->stop();
		HAGGLE_DBG("Joined with protocol killer thread\n");
		delete killer;
	}
}

bool ProtocolManager::init_derived()
{
	int ret;
#define __CLASS__ ProtocolManager

	ret = setEventHandler(EVENT_TYPE_DATAOBJECT_SEND, onSendDataObject);

	if (ret < 0) {
		HAGGLE_ERR("Could not register event handler\n");
		return false;
	}

	ret = setEventHandler(EVENT_TYPE_LOCAL_INTERFACE_UP, onLocalInterfaceUp);

	if (ret < 0) {
		HAGGLE_ERR("Could not register event handler\n");
		return false;
	}

	ret = setEventHandler(EVENT_TYPE_LOCAL_INTERFACE_DOWN, onLocalInterfaceDown);

	if (ret < 0) {
		HAGGLE_ERR("Could not register event handler\n");
		return false;
	}
	
	ret = setEventHandler(EVENT_TYPE_NEIGHBOR_INTERFACE_DOWN, onNeighborInterfaceDown);
	
	if (ret < 0) {
		HAGGLE_ERR("Could not register event handler\n");
		return false;
	}
	
	ret = setEventHandler(EVENT_TYPE_NODE_UPDATED, onNodeUpdated);

	if (ret < 0) {
		HAGGLE_ERR("Could not register event handler\n");
		return false;
	}

	delete_protocol_event = registerEventType("ProtocolManager protocol deletion event", onDeleteProtocolEvent);

	add_protocol_event = registerEventType("ProtocolManager protocol addition event", onAddProtocolEvent);
	
	send_data_object_actual_event = registerEventType("ProtocolManager send data object actual event", onSendDataObjectActual);
	
	protocol_shutdown_timeout_event = registerEventType("ProtocolManager Protocol Shutdown Timeout Event", onProtocolShutdownTimeout);
	
	if (protocol_shutdown_timeout_event < 0) {
		HAGGLE_ERR("Could not register protocol shutdown timeout event\n");
		return false;
	}
	
#ifdef DEBUG
	ret = setEventHandler(EVENT_TYPE_DEBUG_CMD, onDebugCmdEvent);

	if (ret < 0) {
		HAGGLE_ERR("Could not register event handler\n");
		return false;
	}
#endif
	return true;
}

#ifdef DEBUG
void ProtocolManager::onDebugCmdEvent(Event *e)
{	
	if (e->getDebugCmd()->getType() != DBG_CMD_PRINT_PROTOCOLS)
		return;

	protocol_registry_t::iterator it;

	for (it = protocol_registry.begin(); it != protocol_registry.end(); it++) {
		Protocol *p = (*it).second;

		printf("Protocol \'%s\':\n", p->getName());

		printf("\tcommunication interfaces: %s <-> %s\n", 
                       p->getLocalInterface() ? p->getLocalInterface()->getIdentifierStr() : "None",  
                       p->getPeerInterface() ?  p->getPeerInterface()->getIdentifierStr() : "None");
		if (p->getQueue() && p->getQueue()->size()) {
			printf("\tQueue:\n");
			p->getQueue()->print();
		} else {
			printf("\tQueue: empty\n");
		}
	}
}
#endif /* DEBUG */

void ProtocolManager::onAddProtocolEvent(Event *e)
{	
	registerProtocol(static_cast<Protocol *>(e->getData()));
}

bool ProtocolManager::registerProtocol(Protocol *p)
{
	protocol_registry_t::iterator it;

	if (!p)
		return false;
	
	// We are in shutdown, so do not accept this protocol to keep running.
	if (getState() > MANAGER_STATE_RUNNING) {
		p->shutdown();
		if (!p->isDetached()) {
			p->join();
			HAGGLE_DBG("Joined with protocol %s\n", p->getName());
		}
		return false;
	}
		
	if (protocol_registry.insert(make_pair(p->getId(), p)).second == false) {
		HAGGLE_ERR("Protocol %s already registered!\n", p->getName());
		return false;
	}

	p->setRegistered();
	
	HAGGLE_DBG("Protocol %s registered\n", p->getName());
	
	return true;
}

void ProtocolManager::onNodeUpdated(Event *e)
{
	if (!e)
		return;

	NodeRef& node = e->getNode();
	NodeRefList& nl = e->getNodeList();

	if (!node)
		return;

	// Check if there are any protocols that are associated with the updated nodes.
	for (NodeRefList::iterator it = nl.begin(); it != nl.end(); it++) {
		NodeRef& old_node = *it;
		old_node.lock();

		for (InterfaceRefList::const_iterator it2 = old_node->getInterfaces()->begin(); 
			it2 != old_node->getInterfaces()->end(); it2++) {
				const InterfaceRef& iface = *it2;

				for (protocol_registry_t::iterator it3 = protocol_registry.begin(); it3 != protocol_registry.end(); it3++) {
					Protocol *p = (*it3).second;

					if (p->isForInterface(iface)) {
						HAGGLE_DBG("Setting peer node %s on protocol %s\n", node->getName().c_str(), p->getName());
						p->setPeerNode(node);
					}
				}
		}
		old_node.unlock();
	}
}

void ProtocolManager::onDeleteProtocolEvent(Event *e)
{
	Protocol *p;

	if (!e)
		return;

	p = static_cast < Protocol * >(e->getData());

	if (!p)
		return;
		
	if (protocol_registry.find(p->getId()) == protocol_registry.end()) {
		HAGGLE_ERR("Trying to unregister protocol %s, which is not registered\n", p->getName());
		return;
	}

	protocol_registry.erase(p->getId());
	
	HAGGLE_DBG("Removing protocol %s\n", p->getName());

	/*
	  Either we join the thread here, or we detach it when we start it.
	*/
	if (!p->isDetached()) {
		p->join();
		HAGGLE_DBG("Joined with protocol %s\n", p->getName());
		delete p;
	}

	if (getState() == MANAGER_STATE_SHUTDOWN) {
		if (protocol_registry.empty()) {
			unregisterWithKernel();
		} 
#if defined(DEBUG)
		else {
			for (protocol_registry_t::iterator it = protocol_registry.begin(); it != protocol_registry.end(); it++) {
				Protocol *p = (*it).second;
				HAGGLE_DBG("Protocol \'%s\' still registered\n", p->getName());
			}

		}
#endif
	}
}

void ProtocolManager::onProtocolShutdownTimeout(Event *e)
{
	HAGGLE_DBG("Checking for still registered protocols: num registered=%lu\n", 
		   protocol_registry.size());
	
	if (!protocol_registry.empty()) {
		while (!protocol_registry.empty()) {
			Protocol *p = (*protocol_registry.begin()).second;
			protocol_registry.erase(p->getId());
			HAGGLE_DBG("Protocol \'%s\' still registered after shutdown. Detaching it!\n", p->getName());
			
			// We are not going to join with these threads, so we detach.
			if (p->isRunning()) {
				// In detached state, the protocol will delete itself
				// once it stops running.
				p->detach();
				p->cancel();
				
				// We have to clear the protocol's send queue as it may contain
				// data objects whose send verdict managersa are
				// waiting for. Clearing the queue will evict all data objects
				// from its queue with a FAILURE verdict.
				p->closeAndClearQueue();
			} else {
				delete p;
			}
		}
		unregisterWithKernel();
	}
}

/* Close all server protocols so that we cannot create new clients. */
void ProtocolManager::onPrepareShutdown()
{
	HAGGLE_DBG("%lu protocols are registered, shutting down servers.\n", protocol_registry.size());
	
	// Go through the registered protocols
	protocol_registry_t::iterator it = protocol_registry.begin();
	
	for (; it != protocol_registry.end(); it++) {
		// Tell the server protocol to shutdown, with the exception of the
		// application IPC protocol
		if ((*it).second->isServer() && !((*it).second->isApplication())) {
			(*it).second->shutdown();
		}
	}
	signalIsReadyForShutdown();
}
/*
	Do not stop protocols until we are in shutdown, because other managers
	may rely on protocol services while they are preparing for shutdown.
 */
void ProtocolManager::onShutdown()
{	
	HAGGLE_DBG("%lu protocols are registered.\n", protocol_registry.size());
	
	if (protocol_registry.empty()) {
		unregisterWithKernel();
	} else {
		// Go through the registered protocols
		protocol_registry_t::iterator it = protocol_registry.begin();

		for (; it != protocol_registry.end(); it++) {
			// Tell this protocol we're shutting down!
			(*it).second->shutdown();
		}
		
		// In case some protocols refuse to shutdown, launch a thread that sleeps for a while
		// before it schedules an event that forcefully kills the protocols.
		// Note that we need to use this thread to implement a timeout because in shutdown
		// the event queue executes as fast as it can, hence delayed events do not work.
		killer = new ProtocolKiller(protocol_shutdown_timeout_event, 15000, kernel);
		
		if (killer) {
			killer->start();
		}
	}
}

#if defined(OS_WINDOWS_XP) && !defined(DEBUG)
// This is here to avoid a warning with catching the exception in the functions
// below.
#pragma warning( push )
#pragma warning( disable: 4101 )
#endif

class LocalByTypeCriteria : public InterfaceStore::Criteria
{
	Interface::Type_t type;
public:
	LocalByTypeCriteria(Interface::Type_t _type) : type(_type) {}
	virtual bool operator() (const InterfaceRecord& ir) const
	{
		if (ir.iface->getType() == type && ir.iface->isLocal() && ir.iface->isUp())
			return true;
		return false;
	}
};

class PeerParentCriteria : public InterfaceStore::Criteria
{
	InterfaceRef parent;
	InterfaceRef peerIface;
public:
	PeerParentCriteria(const InterfaceRef& iface) : parent(NULL), peerIface(iface) {}
	virtual bool operator() (const InterfaceRecord& ir)
	{
		if (parent && ir.iface && parent == ir.iface && ir.iface->isUp())
			return true;

		if (ir.iface == peerIface)
			parent = ir.parent;

		return false;
	}
};

Protocol *ProtocolManager::getSenderProtocol(const ProtType_t type, const InterfaceRef& peerIface)
{
	Protocol *p = NULL;
	protocol_registry_t::iterator it = protocol_registry.begin();
	InterfaceRef localIface = NULL;
	InterfaceRefList ifl;

	// Go through the list of current protocols until we find one:
	for (; it != protocol_registry.end(); it++) {
		p = (*it).second;

		// Is this protocol the one we're interested in?
		if (p->isSender() && type == p->getType() && 
		    p->isForInterface(peerIface) && 
		    !p->isGarbage() && !p->isDone()) {
			break;
		}
		
		p = NULL;
	}
	
	// Did we find a protocol?
	if (p == NULL) {
		// Nope. Find a suitable local interface to associate with the protocol
		kernel->getInterfaceStore()->retrieve(PeerParentCriteria(peerIface), ifl);
		
		// Parent interface found?
		if (ifl.size() != 0) {
			// Create a new one:
			switch (type) {
#if defined(ENABLE_BLUETOOTH)
				case Protocol::TYPE_RFCOMM:
					// We always grab the first local Bluetooth interface
					p = new ProtocolRFCOMMSender(ifl.pop(), peerIface, RFCOMM_DEFAULT_CHANNEL, this);
					break;
#endif				
				case Protocol::TYPE_TCP:
					// TODO: should retrieve local interface by querying for the parent interface of the peer.
					p = new ProtocolTCPSender(ifl.pop(), peerIface, TCP_DEFAULT_PORT, this);
					break;                           
				case Protocol::TYPE_LOCAL:
					// FIXME: shouldn't be able to get here!
					HAGGLE_DBG("No local sender protocol running!\n");
					break;
				case Protocol::TYPE_UDP:
					// FIXME: shouldn't be able to get here!
					HAGGLE_DBG("No UDP sender protocol running!\n");
					break;
#if defined(ENABLE_MEDIA)
				case Protocol::TYPE_MEDIA:
					p = new ProtocolMedia(NULL, peerIface, this);
					break;
#endif
				default:
					HAGGLE_DBG("Unable to create client sender protocol for type %ld\n", type);
					break;
			}
		}
		// Were we successful?
		if (p) {
			if (!p->init() || !registerProtocol(p)) {
				HAGGLE_ERR("Could not initialize protocol %s\n", p->getName());
				delete p;
				p = NULL;
			}
		}
	}
	// Return any found or created protocol:
	return p;
}

// FIXME: seems to be unused. Delete?
Protocol *ProtocolManager::getReceiverProtocol(const ProtType_t type, const InterfaceRef& iface)
{
	Protocol *p = NULL;
	protocol_registry_t::iterator it = protocol_registry.begin();

	// For an explanation of how this function works, see getSenderProtocol
	for (; it != protocol_registry.end(); it++) {
		p = (*it).second;

		if (p->isReceiver() && type == p->getType() && p->isForInterface(iface) && !p->isGarbage() && !p->isDone()) {
			if (type == Protocol::TYPE_UDP || type == Protocol::TYPE_LOCAL)
				break;
			else if (p->isConnected())
				break;
		}

		p = NULL;
	}

	if (p == NULL) {
		switch (type) {
#if defined(ENABLE_BLUETOOTH)
                        case Protocol::TYPE_RFCOMM:
				// FIXME: write a correct version of this line:
				p = NULL;//new ProtocolRFCOMMReceiver(0, iface, RFCOMM_DEFAULT_CHANNEL, this);
			break;
#endif
                        case Protocol::TYPE_TCP:
				// FIXME: write a correct version of this line:
				p = NULL;//new ProtocolTCPReceiver(0, (struct sockaddr *) NULL, iface, this);
			break;

#if defined(ENABLE_MEDIA)
		case Protocol::TYPE_MEDIA:
			// does not apply to protocol media
			break;
#endif
		default:
			HAGGLE_DBG("Unable to create client receiver protocol for type %ld\n", type);
			break;
		}
		
		if (p) {
			if (!p->init() || !registerProtocol(p)) {
				HAGGLE_ERR("Could not initialize or register protocol %s\n", p->getName());
				delete p;
				p = NULL;
			}
		}
	}

	return p;
}

Protocol *ProtocolManager::getServerProtocol(const ProtType_t type, const InterfaceRef& iface)
{
	Protocol *p = NULL;
	protocol_registry_t::iterator it = protocol_registry.begin();

	// For an explanation of how this function works, see getSenderProtocol

	for (; it != protocol_registry.end(); it++) {
		p = (*it).second;

		// Assume that we are only looking for a specific type of server
		// protocol, i.e., there is only one server for multiple interfaces
		// of the same type
		if (p->isServer() && type == p->getType())
			break;
		
		p = NULL;
	}

	if (p == NULL) {
		switch (type) {
#if defined(ENABLE_BLUETOOTH)
                        case Protocol::TYPE_RFCOMM:
				p = new ProtocolRFCOMMServer(iface, this);
                                break;
#endif
                        case Protocol::TYPE_TCP:
                                p = new ProtocolTCPServer(iface, this, tcpServerPort, tcpBacklog);
                                break;
                                
#if defined(ENABLE_MEDIA)
			case Protocol::TYPE_MEDIA:
				/*
                                if (!strstr(iface->getMacAddrStr(), "00:00:00:00")) {				
						p = new ProtocolMediaServer(iface, this);
        			}
				*/
				break;
#endif
		default:
			HAGGLE_DBG("Unable to create server protocol for type %ld\n", type);
			break;
		}
		
		if (p) {
			if (!p->init() || !registerProtocol(p)) {
				HAGGLE_ERR("Could not initialize or register protocol %s\n", p->getName());
				delete p;
				p = NULL;
			}
		}
	}

	return p;
}

#if defined(OS_WINDOWS_XP) && !defined(DEBUG)
#pragma warning( pop )
#endif

void ProtocolManager::onWatchableEvent(const Watchable& wbl)
{
	protocol_registry_t::iterator it = protocol_registry.begin();

	HAGGLE_DBG("Receive on %s\n", wbl.getStr());

	// Go through each protocol in turn:
	for (; it != protocol_registry.end(); it++) {
		Protocol *p = (*it).second;

		// Did the Watchable belong to this protocol
		if (p->hasWatchable(wbl)) {
			// Let the protocol handle whatever happened.
			p->handleWatchableEvent(wbl);
			return;
		}
	}

	HAGGLE_DBG("Was asked to handle a socket no protocol knows about!\n");
	// Should not happen, but needs to be dealt with because if it isn't,
	// the kernel will call us again in an endless loop!

	kernel->unregisterWatchable(wbl);

	CLOSE_SOCKET(wbl.getSocket());
}

void ProtocolManager::onLocalInterfaceUp(Event *e)
{
	if (!e)
		return;

	InterfaceRef iface = e->getInterface();

	if (!iface)
		return;

	const Addresses *adds = iface->getAddresses();
	
	for (Addresses::const_iterator it = adds->begin() ; it != adds->end() ; it++) {
		switch((*it)->getType()) {
		case Address::TYPE_IPV4:
#if defined(ENABLE_IPv6)
		case Address::TYPE_IPV6:
#endif
			getServerProtocol(Protocol::TYPE_TCP, iface);
			return;			
#if defined(ENABLE_BLUETOOTH)
		case Address::TYPE_BLUETOOTH:
			getServerProtocol(Protocol::TYPE_RFCOMM, iface);
			return;
#endif			
#if defined(ENABLE_MEDIA)
			// FIXME: should probably separate loop interfaces from media interfaces somehow...
		case Address::TYPE_FILEPATH:
			getServerProtocol(Protocol::TYPE_MEDIA, iface);
			return;
#endif
			
		default:
			break;
		}
	}
	
	HAGGLE_DBG("Interface with no known address type - no server started\n");
}

void ProtocolManager::onLocalInterfaceDown(Event *e)
{
	InterfaceRef& iface = e->getInterface();

	if (!iface)
		return;

	HAGGLE_DBG("Local interface [%s] went down, checking for associated protocols\n", iface->getIdentifierStr());

	// Go through the protocol list
	protocol_registry_t::iterator it = protocol_registry.begin();

	for (;it != protocol_registry.end(); it++) {
		Protocol *p = (*it).second;

		/* 
		Never bring down our application IPC protocol when
		application interfaces go down (i.e., applications deregister).
		*/
		if (p->getLocalInterface()->getType() == Interface::TYPE_APPLICATION_PORT) {
			continue;
		}
		// Is the associated with this protocol?
		if (p->isForInterface(iface)) {
			/*
			   NOTE: I am unsure about how this should be done. Either:

			   p->handleInterfaceDown();

			   or:

			   protocol_list_mutex->unlock();
			   p->handleInterfaceDown();
			   return;

			   or:

			   protocol_list_mutex->unlock();
			   p->handleInterfaceDown();
			   protocol_list_mutex->lock();
			   (*it) = protocol_registry.begin();

			   The first has the benefit that it doesn't assume that there is
			   only one protocol per interface, but causes the deletion of the
			   interface to happen some time in the future (as part of event
			   queue processing), meaning the protocol will still be around,
			   and may be given instructions before being deleted, even when
			   it is incapable of handling instructions, because it should
			   have been deleted.

			   The second has the benefit that if the protocol tries to have
			   itself deleted, it happens immediately (because there is no
			   deadlock), but also assumes that there is only one protocol per
			   interface, which may or may not be true.

			   The third is a mix, and has the pros of both the first and the
			   second, but has the drawback that it repeatedly locks and
			   unlocks the mutex, and also needs additional handling so it
			   won't go into an infinite loop (a set of protocols that have
			   already handled the interface going down, for instance).

			   For now, I've chosen the first solution.
			 */
			// Tell the protocol to handle this:
			HAGGLE_DBG("Shutting down protocol %s because local interface [%s] went down\n",
				p->getName(), iface->getIdentifierStr());
			p->handleInterfaceDown(iface);
		}
	}
}

void ProtocolManager::onNeighborInterfaceDown(Event *e)
{
	InterfaceRef& iface = e->getInterface();
	
	if (!iface)
		return;
	
	HAGGLE_DBG("Neighbor interface [%s] went away, checking for associated protocols\n", iface->getIdentifierStr());

	// Go through the protocol list
	protocol_registry_t::iterator it = protocol_registry.begin();
	
	for (;it != protocol_registry.end(); it++) {
		Protocol *p = (*it).second;
		
		/* 
		 Never bring down our application IPC protocol when
		 application interfaces go down (i.e., applications deregister).
		 */
		if (p->getLocalInterface()->getType() == Interface::TYPE_APPLICATION_PORT) {
			continue;
		}
		// Is the associated with this protocol?
		if (p->isClient() && p->isForInterface(iface)) {
			HAGGLE_DBG("Shutting down protocol %s because neighbor interface [%s] went away\n",
				   p->getName(), iface->getIdentifierStr());
			p->handleInterfaceDown(iface);
		}
	}
}

void ProtocolManager::onSendDataObject(Event *e)
{
	/*
		Since other managers might want to modify the data object before it is
		actually sent, we delay the actual send processing by sending a private
		event to ourselves, effectively rescheduling our own processing of this
		event to occur just after this event.
	*/
	kernel->addEvent(new Event(send_data_object_actual_event, e->getDataObject(), e->getNodeList()));
}

void ProtocolManager::onSendDataObjectActual(Event *e)
{
	int numTx = 0;

	if (!e || !e->hasData())
		return;

	// Get a copy to work with
	DataObjectRef dObj = e->getDataObject();

	// Get target list:
	NodeRefList *targets = (e->getNodeList()).copy();

	if (!targets) {
		HAGGLE_ERR("no targets in data object when sending\n");
		return;
	}

	unsigned int numTargets = targets->size();

	// Go through all targets:
	while (!targets->empty()) {
		
		// A current target reference
		NodeRef targ = targets->pop();
		
		if (!targ) {
			HAGGLE_ERR("Target num %u is NULL!\n", 
				   numTargets);
			numTargets--;
			continue;
		}

		HAGGLE_DBG("Sending to target %s \n", 
			   targ->getName().c_str());
		
		// If we are going to loop through the node's interfaces, we need to lock the node.
		targ.lock();	
		
		const InterfaceRefList *interfaces = targ->getInterfaces();
		
		// Are there any interfaces here?
		if (interfaces == NULL || interfaces->size() == 0) {
			// No interfaces for target, so we generate a
			// send failure event and skip the target
		
			HAGGLE_DBG("Target %s has no interfaces\n", targ->getName().c_str());

			targ.unlock();

			kernel->addEvent(new Event(EVENT_TYPE_DATAOBJECT_SEND_FAILURE, dObj, targ));
			numTargets--;
			continue;
		}
		
		/*	
			Find the target interface that suits us best
			(we assume that for any remote target
			interface we have a corresponding local interface).
		*/
		InterfaceRef peerIface = NULL;
		bool done = false;
		
		InterfaceRefList::const_iterator it = interfaces->begin();
		
		//HAGGLE_DBG("Target node %s has %lu interfaces\n", targ->getName().c_str(), interfaces->size());

		for (; it != interfaces->end() && done == false; it++) {
			InterfaceRef iface = *it;
			
			// If this interface is up:
			if (iface->isUp()) {
				
				if (iface->getAddresses()->empty()) {
					HAGGLE_DBG("Interface %s:%s has no addresses - IGNORING.\n",
						   iface->getTypeStr(), iface->getIdentifierStr());
					continue;
				}
				
				switch (iface->getType()) {
#if defined(ENABLE_BLUETOOTH)
				case Interface::TYPE_BLUETOOTH:
					/*
					  Select Bluetooth only if there are no Ethernet or WiFi
					  interfaces.
					*/
					if (!iface->getAddress<BluetoothAddress>()) {
						HAGGLE_DBG("Interface %s:%s has no Bluetooth address - IGNORING.\n",
							   iface->getTypeStr(), iface->getIdentifierStr());
						break;
					}
					
					if (!peerIface)
						peerIface = iface;
					else if (peerIface->getType() != Interface::TYPE_ETHERNET &&
						 peerIface->getType() != Interface::TYPE_WIFI)
						peerIface = iface;
					break;
#endif
#if defined(ENABLE_ETHERNET)
				case Interface::TYPE_ETHERNET:
					/*
					  Let Ethernet take priority over the other types.
					*/
					if (!iface->getAddress<IPv4Address>()
#if defined(ENABLE_IPv6)
					    && !iface->getAddress<IPv6Address>()
#endif
						) {
						HAGGLE_DBG("Interface %s:%s has no IPv4 or IPv6 addresses - IGNORING.\n",
							   iface->getTypeStr(), iface->getIdentifierStr());
						break;
					}
					if (!peerIface)
						peerIface = iface;
					else if (peerIface->getType() == Interface::TYPE_BLUETOOTH ||
						 peerIface->getType() == Interface::TYPE_WIFI)
						peerIface = iface;
					break;
				case Interface::TYPE_WIFI:
					if (!iface->getAddress<IPv4Address>() 
#if defined(ENABLE_IPv6)
					    && !iface->getAddress<IPv6Address>()
#endif
						) {
						HAGGLE_DBG("Interface %s:%s has no IPv4 or IPv6 addresses - IGNORING.\n",
							   iface->getTypeStr(), iface->getIdentifierStr());
						break;
					}
					if (!peerIface)
						peerIface = iface;
					else if (peerIface->getType() == Interface::TYPE_BLUETOOTH &&
						 peerIface->getType() != Interface::TYPE_ETHERNET)
						peerIface = iface;
					break;
#endif // ENABLE_ETHERNET
				case Interface::TYPE_APPLICATION_PORT:
				case Interface::TYPE_APPLICATION_LOCAL:
                                        
					if (!iface->getAddress<IPv4Address>() 
#if defined(ENABLE_IPv6)
					    && !iface->getAddress<IPv6Address>()
#endif
						) {
						HAGGLE_DBG("Interface %s:%s has no IPv4 or IPv6 addresses - IGNORING.\n",
							   iface->getTypeStr(), iface->getIdentifierStr());
						break;
					}
					// Not much choise here.
					if (targ->getType() == Node::TYPE_APPLICATION) {
						peerIface = iface;
						done = true;
					} else {
						HAGGLE_DBG("ERROR: Node %s is not application, but its interface is\n",
							targ->getName().c_str());
					}
                                        
					break;
#if defined(ENABLE_MEDIA)
				case Interface::TYPE_MEDIA:
					break;
#endif
				case Interface::TYPE_UNDEFINED:
				default:
					break;
				}
			} else {
				//HAGGLE_DBG("Send interface %s was down, ignoring...\n", iface->getIdentifierStr());
			}
		}
		
		// We are done looking for a suitable send interface
		// among the node's interface list, so now we unlock
		// the node.
		targ.unlock();
		
		if (!peerIface) {
			HAGGLE_DBG("No send interface found for target %s. Aborting send of data object!!!\n", 
				targ->getName().c_str());
			// Failed to send to this target, send failure event:
			kernel->addEvent(new Event(EVENT_TYPE_DATAOBJECT_SEND_FAILURE, dObj, targ));
			numTargets--;
			continue;
		}

		// Ok, we now have a target and a suitable interface,
		// now we must figure out a protocol to use when we
		// transmit to that interface
		Protocol *p = NULL;
		
		// We make a copy of the addresses list here so that we do not
		// have to lock the peer interface while we call getSenderProtocol().
		// getSenderProtocol() might do a lookup in the interface store in order
		// to find the local interface which is parent of the peer interface.
		// This might cause a deadlock in case another thread also does a lookup
		// in the interface store while we hold the interface lock.
		const Addresses *adds = peerIface->getAddresses()->copy();
		
		// Figure out a suitable protocol given the addresses associated 
		// with the selected interface
		for (Addresses::const_iterator it = adds->begin(); p == NULL && it != adds->end(); it++) {
			
			switch ((*it)->getType()) {
#if defined(ENABLE_BLUETOOTH)
			case Address::TYPE_BLUETOOTH:
				p = getSenderProtocol(Protocol::TYPE_RFCOMM, peerIface);
				break;
#endif
			case Address::TYPE_IPV4:
#if defined(ENABLE_IPv6)
			case Address::TYPE_IPV6:
#endif
				if (peerIface->isApplication()) {
#ifdef USE_UNIX_APPLICATION_SOCKET
					p = getSenderProtocol(Protocol::TYPE_LOCAL, peerIface);
#else
					p = getSenderProtocol(Protocol::TYPE_UDP, peerIface);
#endif
				}
				else
					p = getSenderProtocol(Protocol::TYPE_TCP, peerIface);
				break;
#if defined(ENABLE_MEDIA)
			case Address::TYPE_FILEPATH:
				p = getSenderProtocol(Protocol::TYPE_MEDIA, peerIface);
				break;
#endif
			default:
				break;
			}
		}
		
		delete adds;
		
                // Send data object to the found protocol:

                if (p) {
			if (p->sendDataObject(dObj, targ, peerIface)) {
				numTx++;
			} else {
				// Failed to send to this target, send failure event:
                                kernel->addEvent(new Event(EVENT_TYPE_DATAOBJECT_SEND_FAILURE, dObj, targ));
			}
		} else {
			HAGGLE_DBG("No suitable protocol found for interface %s:%s!\n", 
				   peerIface->getTypeStr(), peerIface->getIdentifierStr());
			// Failed to send to this target, send failure event:
			kernel->addEvent(new Event(EVENT_TYPE_DATAOBJECT_SEND_FAILURE, dObj, targ));			
		}

		numTargets--;
	}
	
	/* HAGGLE_DBG("Scheduled %d data objects\n", numTx); */

	delete targets;
}

void ProtocolManager::onConfig(Metadata *m)
{
	Metadata *pm = m->getMetadata("TCPServer");

	if (pm) {
		const char *param = pm->getParameter("port");

		if (param) {
			char *endptr = NULL;
			unsigned short port = (unsigned short)strtoul(param, &endptr, 10);
			
			if (endptr && endptr != param) {
				tcpServerPort = port;
				LOG_ADD("# %s: setting TCP server port to %u\n", getName(), tcpServerPort);
			}
		}

		param = pm->getParameter("backlog");

		if (param) {
			char *endptr = NULL;
			int backlog = (int)strtol(param, &endptr, 10);
			
			if (endptr && endptr != param && backlog > 0) {
				tcpBacklog = backlog;
				LOG_ADD("# %s: setting TCP backlog to %d\n", getName(), tcpBacklog);
			}
		}
	}
}
