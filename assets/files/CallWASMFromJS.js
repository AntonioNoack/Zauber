const fs = require("fs");

// Read the wasm binary
const wasmBytes = fs.readFileSync("./Binary.wasm");

// Create imported memory
const memory = new WebAssembly.Memory({
    initial: 64
});

let lib = {}

// Declare imported functions
const imports = {
    js: {
        mem: memory
    },
    env: {
        'zauber_println_wjpkxu': (self, value) => {
            console.log(value);
            return lib["obj_zauber_Unit"]()
        },
        'zauber_Int_plus_rtgkvs': () => {
        },
        'zauber_Int_times_rtgkvs': () => {
        },
    }
};

async function loadAndRun() {
    // Instantiate the module
    const wasmModule = await WebAssembly.instantiate(wasmBytes, imports);

    // Access exported functions
    const { main} = lib = wasmModule.instance.exports;

    // Call the wasm function
    main();
}

// cannot use await here
loadAndRun();