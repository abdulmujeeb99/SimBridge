# SimBridge v2 — Multi-Home SIM Relay
### Receive calls & SMS from multiple home phones in one travel app

---

## What Changed in v2

- **Multiple Home phones supported** — Phone A in Karachi, Phone B in Lahore, both ring on your one travel app
- **SIM Labels** — each incoming call/SMS shows which SIM it came from (e.g. "Karachi SIM")
- **SIM Selector in Dialer** — choose which home phone places the outgoing call
- **SIM Selector in SMS** — choose which home phone sends the SMS

---

## Setup Overview

```
Phone 1 (Karachi)        Phone 2 (Lahore)
SimBridgeHome            SimBridgeHome
SIM Label: "Karachi SIM" SIM Label: "Lahore SIM"
Same pair code: 123456   Same pair code: 123456
Same server URL          Same server URL
        │                        │
        └──────── Server ────────┘
                     │
            Your Travel Phone
            SimBridgeTravel
            Pair code: 123456
            Shows both SIMs
```

---

## Step 1 — Deploy Signaling Server (VPS)

```bash
apt install nodejs npm -y
cd SimBridge/SignalingServer
npm install
node server.js          # runs on port 8080
```

For always-on:
```bash
npm install -g pm2
pm2 start server.js --name simbridge
pm2 save && pm2 startup
```

Open port 8080 TCP in your VPS firewall.

---

## Step 2 — Build SimBridgeHome (install on EACH home phone)

1. Open `SimBridgeHome/` in Android Studio
2. Build APK → install on home phone
3. Grant all permissions
4. Go to **Setup**:
   - SIM Label: `Karachi SIM` (or whatever you want)
   - Server URL: `ws://YOUR_VPS_IP:8080`
   - Pair Code: `123456` (same on all devices)
5. Enable relay switch
6. Disable battery optimization for the app

Repeat for Phone 2 with label `Lahore SIM`, same pair code.

---

## Step 3 — Build SimBridgeTravel (your travel phone)

1. Open `SimBridgeTravel/` in Android Studio
2. Build APK → install on travel phone
3. Go to Setup:
   - Server URL: `ws://YOUR_VPS_IP:8080`
   - Pair Code: `123456` (must match home phones)
4. Status shows both home devices connected

---

## How It Works

### Incoming call on Karachi SIM
```
Caller → Karachi phone → SimBridgeHome detects it
→ sends to server with simLabel="Karachi SIM"
→ server forwards to travel phone
→ travel phone rings, shows "Karachi SIM" badge
→ you answer → audio bridges both ways
```

### Outgoing call from travel
```
Open Dialer → dropdown shows [Karachi SIM] [Lahore SIM]
Select which SIM → dial number
→ that home phone places the cellular call
→ audio bridges back
```

### SMS
- Incoming SMS on any home SIM → forwarded to travel with SIM label shown
- Send SMS: choose which SIM from dropdown → sends via that home phone

---

## Permissions

### Home App
| Permission | Purpose |
|---|---|
| READ_PHONE_STATE | Detect incoming calls |
| ANSWER_PHONE_CALLS | Auto-answer |
| CALL_PHONE | Place outgoing calls |
| SEND_SMS / RECEIVE_SMS | SMS relay |
| RECORD_AUDIO | In-call audio capture |
| FOREGROUND_SERVICE | Keep alive |
| RECEIVE_BOOT_COMPLETED | Auto-start on boot |

### Travel App
| Permission | Purpose |
|---|---|
| RECORD_AUDIO | Your mic during calls |
| INTERNET | WebSocket connection |
| FOREGROUND_SERVICE | Keep connection alive |

---

## Battery Optimization (Important)

On each home phone:
Settings → Battery → SimBridge Home → **Unrestricted** (no restrictions)

On Samsung specifically:
Settings → Device Care → Battery → Background usage limits → Never sleeping apps → Add SimBridgeHome

---

## Root Required?

| Feature | Root needed |
|---|---|
| Detect incoming calls | No |
| Forward SMS | No |
| Send SMS | No |
| Auto-answer call (Android 9+) | No |
| End/reject call (Android 9+) | No |
| Suppress home phone ringer silently | May help |
| Audio injection on some custom ROMs | May help |

---

## Troubleshooting

**Both home phones show in travel app?**
→ Yes — both should appear in the SIM dropdown immediately after connecting

**Only one home phone connecting?**
→ Verify both use identical pair code and server URL
→ Check VPS logs: `pm2 logs simbridge`

**Call audio not working?**
→ Grant RECORD_AUDIO on both home and travel phones
→ Disable battery optimization

**Home phone ringer goes off at home during relay**
→ Manually set home phone to silent/DND, or with root the app can do it automatically
