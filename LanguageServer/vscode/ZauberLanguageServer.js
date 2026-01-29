const path = require("path");
const { LanguageClient, TransportKind } = require("vscode-languageclient/node");

console.log("Starting JS Zauber Language Server");

let client;

function activate(context) {
  console.log("Activating JS Zauber Language Server");

  const jarPath = context.asAbsolutePath(
    path.join("server", "ZauberLanguageServer.jar")
  );

  const serverOptions = {
    command: "java",
    args: ["-jar", jarPath],
    transport: TransportKind.stdio
  };

  const clientOptions = {
    documentSelector: [{ scheme: "file", language: "zauber" }]
  };

  client = new LanguageClient(
    "zauber",
    "Zauber Language Server",
    serverOptions,
    clientOptions
  );

  context.subscriptions.push(client.start());
}

function deactivate() {
  console.log("Deactivating JS Zauber Language Server");
  if (client) {
    return client.stop();
  }
}

module.exports = { activate, deactivate };
