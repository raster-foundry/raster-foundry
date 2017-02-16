# 0011 - Framework for Tool UI
## Context
Within the Tools section, Raster Foundry needs to provide an interface that allows for the linking of the user's resources with Raster Foundry's various processing tools to easily create an imagery processing pipeline from their browser. Through dragging-and-dropping components into the diagramming interface, the user will be able to specify which sources are fed into which processing tools and whether the  output from these tools will be routed into another processing tool or used as a new source of imagery.

Using an already existing robust and well-maintained open-source framework will allow us to focus efforts on building the interface built around the diagramming framework rather than re-invent the wheel.

### Previous Research

In phase 1 of ModelLab, _extensive_ research was done on this topic (see this [original ModelLab issue](https://github.com/azavea/modellab/issues/2)), and the following frameworks were evaluated:

- [JointJS](https://github.com/clientIO/joint)
- [Cytoscape.js](https://github.com/cytoscape/cytoscape.js)
- [Draw2D](http://www.draw2d.org/draw2d/)
- [vis.js](https://github.com/almende/vis)
- [jsplumb](https://github.com/jsplumb/jsPlumb)
- [Blockly](https://developers.google.com/blockly/)

From this research, [JointJS](https://github.com/clientIO/joint) emerged as the recommended choice.

We will build on this research, re-evaluate the recommended library to ensure it remains the best choice given the current architecture of Raster Foundry, and finalize the decision.

### JointJS

There are several key features of JointJS that make it a viable option:

- Completely interactive elements and links
- Custom shapes for elements and links
- Smart routing of links to avoid collisions
- Built-in JSON (de)serialization for graphs
- Event driven
- Touch enabled

The fully evented nature of JointJS makes it ideal for integration. For example, JointJS emits unique events for the following interactions with any element (elements are connected by links):

- position change (including z-axis)
- angle change
- size change
- attribute change
- embedding of another element
- embedding into another element
- transition start/end

As was the case when the initial recommendation was made, JointJS does not provide automatic layouts; elements must be explicitly positioned.

Development of JointJS has continued with a regular pace. JointJS also has a commerical extension, [Rappid](http://jointjs.com/), which ensures some level of commitment to the library.

The commercial offering adds features that aren't necessary for Raster Foundry such as pre-built UI widgets, and interaction components.

#### Integration

Integration of JointJS into Raster Foundry would be done in a manner similar to that of Leaflet. A component could be created to handle the encapsulation of the library and management of the necessary bindings.

Initial diagram data (JSON) could be passed into the component via a one-way binding, while any changes to the diagram would trigger an `updateWorkflow` function that could parse the data and persist the resulting workflow.

```
<rf-workflow-diagram
	initial-diagram="$ctrl.currentWorkflowJson"
	on-diagram-change="$ctrl.updateWorkflow(diagramJson)">
</rf-workflow-diagram>
```

For more complex interactions, a `workflowService` (singleton) could be utilized to manage the processing workflows, their states, and handle communication between the various workflow construction UI components, such as the list of tools the user has access to, and the current diagram. JointJS emits events for essentially any interaction, therefore wrapping it with a service-centric angular component should be straightforward.

```
<rf-workflow-diagram
	initial-diagram="$ctrl.currentWorkflowJson"
	workflow-service="$ctrl.workflowService">
</rf-workflow-diagram>
```

During initialization, the component would create the necessary event listeners that would send any relevant event data to the `workflowService`.


### jsplumb
[jsplumb](https://github.com/jsplumb/jsPlumb), like JointJS, is well-maintained and has a commercial wrapper. It is also lightweight and has few dependencies. It uses all HTML rather than SVG elements. 

However, unlike JointJS, the API is not straightforward. Additionally, the open-source version lacks much of the useful functionality that JointJS provides (pan, zoom, json (de)serialization) and makes these features available only in the commercial version.

#### Integration
Integration of jsplumb would follow the same method described for JointJS.

## Decision

JointJS remains the best choice as the diagramming framework for Raster Foundry's Tool UI. The straightforward API and useful open-source version make it optimal for our use-case. The documentation and examples should also help propel the integration of the library and the implementation of the Tool UI.

JointJS also provides mechanisms for ingesting JSON to intialize graphs will should greatly ease the many aspects of the integration.

## Consequences

The greatest challenges that result from this decision are:
-  the building of the layout logic for the diagrams
-  the design and building of interface that will allow users to add sources and tools to their processing flow

We will also need to familiarize ourselves with the JointJS API, but the high-quality documentation should ease this burden.