// superdood.org/install.html: installs Superdood on a Supernote plugged into this computer, from Chrome or Edge
// (WebUSB), then compiles it ahead of time as the README's adb steps do. One job only: this APK, this app.
// Built into docs/site/install.js by build.sh; the Tango library (ya-webadb, MIT) does the USB and adb.
import { Adb, AdbDaemonTransport } from "@yume-chan/adb";
import { AdbDaemonWebUsbDeviceManager } from "@yume-chan/adb-daemon-webusb";
import AdbWebCredentialStore from "@yume-chan/adb-credential-web";
import { PackageManager } from "@yume-chan/android-bin";

const APK = "/superdood.apk", APP = "ing.ell.mybaddrawings";
const button = document.getElementById("go"), note = document.getElementById("note");
const say = (text) => { note.textContent = text; };

const usb = AdbDaemonWebUsbDeviceManager.BROWSER;
if (!usb) {
  button.disabled = true;
  const steps = document.createElement("a");
  steps.href = "https://github.com/ellinglien/superdood#or-with-android-sideloader-safari-firefox-or-if-the-page-cant-reach-the-supernote";
  steps.textContent = "follow the Android Sideloader steps";
  note.append("This page needs Chrome or Edge on a computer. In this browser, ", steps, " instead.");
}

async function install() {
  const device = await usb.requestDevice();
  if (!device) return say(""); // the chooser was closed
  let adb;
  try {
    say("Connecting. If the Supernote asks to allow USB debugging, tap Allow.");
    const connection = await device.connect();
    const transport = await AdbDaemonTransport.authenticate({
      serial: device.serial, connection, credentialStore: new AdbWebCredentialStore("Superdood installer"),
    });
    adb = new Adb(transport);

    say("Downloading Superdood…");
    const response = await fetch(APK, { cache: "no-store" });
    if (!response.ok) throw new Error("download " + response.status);
    const apk = await response.blob();

    say("Installing…");
    await new PackageManager(adb).installStream(apk.size, apk.stream());

    say("Making it fast. This takes about a minute…");
    await adb.subprocess.noneProtocol.spawnWaitText(["cmd", "package", "compile", "-m", "speed", "-f", APP]);

    say("Done! Unplug the Supernote and open Superdood.");
  } catch (e) {
    console.error(e);
    // Another program (adb, Android Sideloader) holding the USB connection fails the claim with NetworkError.
    if (e && (e.name === "NetworkError" || /claim|busy|in use/i.test(e.message || ""))) {
      say("Something else on this computer is using the Supernote's USB connection (adb or Android Sideloader, say). Close it and try again.");
    } else {
      say("That didn't work: " + (e && e.message ? e.message : e) + ". Unplug the Supernote, plug it back in and try again.");
    }
  } finally {
    if (adb) await adb.close().catch(() => {});
  }
}

button.addEventListener("click", () => {
  button.disabled = true;
  install().finally(() => { button.disabled = !usb; });
});
