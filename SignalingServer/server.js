// SimBridge Signaling Server — Multi-Home Edition
// Supports: many home phones + one travel phone under same pair code
// Deploy on any VPS with Node.js

const WebSocket = require('ws');
const http = require('http');

const server = http.createServer((req, res) => {
    res.writeHead(200, { "Content-Type": "text/plain" });
    res.end("SimBridge signaling server running 🚀");
});
const wss = new WebSocket.Server({ server });
const PORT = process.env.PORT || 3000;

/**
 * clients map:
 *  deviceId -> {
 *    ws,
 *    role: 'home' | 'travel',
 *    pairCode: '148262',
 *    simLabel: 'Home SIM',   // home only — user-defined label
 *    pairedTravelId: null,       // home only
 *    pairedHomeIds: [],          // travel only — list of all paired home deviceIds
 *  }
 */
const clients = new Map();

wss.on('connection', (ws) => {
    let deviceId = null;

    ws.on('message', (data) => {
        let msg;
        try { msg = JSON.parse(data); } catch (e) { return; }

        switch (msg.type) {

            case 'register':
                deviceId = msg.deviceId;
                clients.set(deviceId, {
                    ws,
                    role: msg.role,
                    pairCode: msg.pairCode,
                    simLabel: msg.simLabel || (msg.role === 'home' ? 'Home' : null),
                    pairedTravelId: null,
                    pairedHomeIds: []
                });
                console.log(`Registered: ${deviceId} | role=${msg.role} | simLabel="${msg.simLabel}"`);
                tryPair(deviceId);
                break;

            case 'call_incoming':
            case 'call_ended':
            case 'sms_incoming':
            case 'call_answered':
            case 'call_rejected':
            case 'call_outgoing':
            case 'sms_send':
            case 'sms_sent':
            case 'audio_chunk':
            case 'dtmf':
                routeMessage(fromId = deviceId, msg);
                break;

            case 'ping':
                send(ws, { type: 'pong' });
                break;
        }
    });

    ws.on('close', () => {
        if (!deviceId) return;
        const client = clients.get(deviceId);
        if (!client) return;

        if (client.role === 'home') {
            if (client.pairedTravelId) {
                const travel = clients.get(client.pairedTravelId);
                if (travel) {
                    send(travel.ws, { type: 'home_disconnected', deviceId, simLabel: client.simLabel });
                    travel.pairedHomeIds = travel.pairedHomeIds.filter(id => id !== deviceId);
                }
            }
        } else {
            for (const homeId of client.pairedHomeIds) {
                const home = clients.get(homeId);
                if (home) { send(home.ws, { type: 'peer_disconnected' }); home.pairedTravelId = null; }
            }
        }
        clients.delete(deviceId);
        console.log(`Disconnected: ${deviceId}`);
    });

    ws.on('error', (err) => console.error('WS error:', err.message));
    send(ws, { type: 'connected' });
});

function tryPair(newId) {
    const newClient = clients.get(newId);
    if (!newClient) return;

    for (const [id, client] of clients) {
        if (id === newId) continue;
        if (client.pairCode !== newClient.pairCode) continue;

        if (newClient.role === 'home' && client.role === 'travel') {
            newClient.pairedTravelId = id;
            client.pairedHomeIds.push(newId);
            send(newClient.ws, { type: 'paired', travelDeviceId: id });
            send(client.ws, { type: 'home_connected', deviceId: newId, simLabel: newClient.simLabel });
            console.log(`Paired: home ${newId} (${newClient.simLabel}) <-> travel ${id}`);

        } else if (newClient.role === 'travel' && client.role === 'home') {
            newClient.pairedHomeIds.push(id);
            client.pairedTravelId = newId;
            send(client.ws, { type: 'paired', travelDeviceId: newId });
            send(newClient.ws, { type: 'home_connected', deviceId: id, simLabel: client.simLabel });
            console.log(`Paired: travel ${newId} <-> home ${id} (${client.simLabel})`);
        }
    }

    if (newClient.role === 'travel' && newClient.pairedHomeIds.length === 0)
        send(newClient.ws, { type: 'waiting_for_home' });
    else if (newClient.role === 'home' && !newClient.pairedTravelId)
        send(newClient.ws, { type: 'waiting_for_travel' });
}

/**
 * Home → Travel: attach simLabel + homeDeviceId so travel knows which SIM
 * Travel → Home: msg.targetHomeId tells which home to command (calls/audio/sms)
 */
function routeMessage(fromId, msg) {
    const sender = clients.get(fromId);
    if (!sender) return;
    msg.from = fromId;

    if (sender.role === 'home') {
        msg.simLabel    = sender.simLabel;
        msg.homeDeviceId = fromId;
        const travel = clients.get(sender.pairedTravelId);
        if (travel) send(travel.ws, msg);

    } else if (sender.role === 'travel') {
        const targetId = msg.targetHomeId;
        if (targetId) {
            const home = clients.get(targetId);
            if (home) send(home.ws, msg);
        } else {
            // broadcast (e.g. call_ended with no specific target)
            for (const homeId of sender.pairedHomeIds) {
                const home = clients.get(homeId);
                if (home) send(home.ws, msg);
            }
        }
    }
}

function send(ws, obj) {
    if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj));
}

setInterval(() => {
    for (const [, client] of clients) send(client.ws, { type: 'ping' });
}, 30000);

server.listen(PORT, () => {
    console.log(`SimBridge Multi-Home Server on port ${PORT}`);
});
