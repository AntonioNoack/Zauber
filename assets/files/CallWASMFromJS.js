const fs = require("fs");

// Read the wasm binary
const wasmBytes = fs.readFileSync("./Binary.wasm");

// Create imported memory
const memory = new WebAssembly.Memory({
    initial: 64
});

// Declare imported functions
const imports = {
    js: {
        mem: memory
    },
    env: {
        'zauber_println_wjpkxu': (self, value) => {
            console.log("WASM says:", value);
            return 0
        },
        'zauber.Int_plus_rtgkvs': () => {},
    }
};

async function main() {
    // Instantiate the module
    const wasmModule = await WebAssembly.instantiate(wasmBytes, imports);

    // Access exported functions
    const { test0_main_0 } = wasmModule.instance.exports;

    // Call the wasm function
    test0_main_0(0);
}

// cannot use await here
main();