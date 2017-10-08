Raster Foundry Angular Style Guide
===========================

Documentation comments
---------------------
Documentation comments should be in [JSDoc](http://usejsdoc.org/) format.

Documentation comments should use the same style as our Scala comments:

```javascript
/** This is a good multi-line comment
  *
  * good stuff
  */
```

```javascript
/** This is a bad multi-line comment
 *
 *  bad stuff
 */
```

Dangling underscores
------------------------
Should be used to denote "private" variables, which should only be accessed via "this".

```javascript
// Good
this._currentLayer = LayerService.getFirstLayer();

// Bad
this._currentLayer = LayerService._internalLayerList[0];
```

Note that this usage may be unavoidable when dealing with outside libraries.
In this case, the check can be disabled for one line with:
```javascript
// eslint-disable-next-line no-underscore-dangle
```


Promises
---------
Use
```
$q((resolve, reject) => {...})
```
instead of
```
let deferred = $q.defer()
```
The former avoids the common promise pitfall of not having a failure condition on a promise,  
since most linters will complain if you don't use a defined function parameter
