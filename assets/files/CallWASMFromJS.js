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
        'zauber_Array_get_724980837_sfuzcq': () => {
            throw 'Implement Array-access'
        },
        'zauber_Array_set_724980837_14ox0cd': () => {
            throw 'Implement Array-access'
        }
    }
};

async function loadAndRun() {
    // Instantiate the module
    const wasmModule = await WebAssembly.instantiate(wasmBytes, imports);

    // Access exported functions
    const {main} = lib = wasmModule.instance.exports;

    // Call the wasm function
    main();
}

// cannot use await here
loadAndRun();