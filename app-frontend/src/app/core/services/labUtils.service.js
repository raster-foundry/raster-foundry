export default (app) => {
    class LabUtils {
        constructor() { }

        getToolImports(toolDefinition) {
            let inputsJson = [];

            let json = Object.assign({}, toolDefinition);
            let inputs = [json];
            while (inputs.length) {
                let input = inputs.pop();
                let args = input.args;
                if (args) {
                    let tool = this.getNodeLabel(input);
                    if (!Array.isArray(args)) {
                        args = Object.values(args);
                    }
                    inputs = inputs.concat(args.map((a) => {
                        return Object.assign({
                            parent: tool
                        }, a);
                    }));
                } else {
                    inputsJson.push(input);
                }
            }
            return inputsJson;
        }

        getNodeLabel(json) {
            if (json.metadata && json.metadata.label) {
                return json.metadata.label;
            }
            return json.apply;
        }

        createPorts(inputs, outputs) {
            let ports = [];
            let inputList = Array.isArray(inputs) ?
                inputs : Array(inputs).fill();

            ports = inputList.map((_, idx) => {
                return {
                    id: `input-${idx}`,
                    label: `input-${idx}`,
                    group: 'inputs'
                };
            });

            ports = ports.concat(outputs.map(o => {
                return {
                    id: o,
                    group: 'outputs'
                };
            }));

            return ports;
        }

        getNodeArgs(node) {
            if (node.args) {
                return Array.isArray(node.args) ? node.args : Object.values(node.args);
            }
            return [];
        }

        getNodeAttributes(node) {
            let rectInputs = this.getNodeArgs(node).length;
            let rectOutputs = ['Output'];
            let ports = this.createPorts(rectInputs, rectOutputs);
            return Object.assign({
                id: node.id,
                label: this.getNodeLabel(node),
                type: node.type ? node.type : 'function',
                inputs: rectInputs,
                outputs: rectOutputs,
                tag: node.tag,
                ports: ports
            }, {
                operation: node.apply,
                value: node.constant,
                positionOverride: node.metadata && node.metadata.positionOverride
            });
        }
    }

    app.service('labUtils', LabUtils);
};
