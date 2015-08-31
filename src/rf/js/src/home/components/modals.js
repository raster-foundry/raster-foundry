'use strict';

var React = require('react');

var UploadModal = React.createBackboneClass({
    render: function() {
        return (
            <div aria-hidden="true" aria-labelledby="import-imagery-modal" role="dialog" tabIndex={-1} id="import-imagery-modal" className="modal import-modal fade">
                {/* Import Files Modal */}
                <div className="pane animated fadeInDown active" id="pane-1">
                    <div className="modal-dialog modal-lg">
                        <div className="modal-content">
                            <button aria-label="Close" data-dismiss="modal" className="close" type="button"><i className="rf-icon-cancel" /></button>
                            <div className="modal-body no-padding">
                                <div className="container-fluid">
                                    <div className="row">
                                        <div className="col-md-7 import-browse">
                                            <i className="rf-icon-upload-cloud-outline" />
                                            <h3 className="font-400">Drag &amp; Drop</h3>
                                            <h4 className="font-300">to store your imagery here, or <a href="#">browse</a>.</h4>
                                            <em>zip files or single image files accepted</em>
                                            <input type="file" id="files"  multiple onChange={this.props.handleFiles} />
                                        </div>
                                        <div className="col-md-5 import-uri">
                                            <i className="rf-icon-link" />
                                            <h3 className="font-400">Upload with a URI</h3>
                                            <h4 className="font-300">Is your imagery hosted somewhere already? Enter the URI to import.</h4>
                                            <em>Examples: S3, DropBox, FTP</em>
                                            <form>
                                                <div className="form-group">
                                                    <label>URI</label>
                                                    <input type="url" className="form-control" />
                                                </div>
                                                <div className="form-action text-right">
                                                    {/* <input type="submit" class="btn btn-primary" value="Continue"> */}
                                                    <button data-pane-target="#pane-2" data-toggle-pane className="btn btn-secondary">Continue</button>
                                                </div>
                                            </form>
                                        </div>
                                    </div>
                                </div>
                            </div> {/* /.modal-body */}
                        </div> {/* /.modal-content */}
                    </div> {/* /.modal-dialog */}
                </div> {/* /#pane-1 */}
                <div className="pane animated fadeInDown" id="pane-2">
                    <div className="modal-dialog">
                        <div className="modal-content">
                            <button aria-label="Close" data-dismiss="modal" className="close" type="button"><span aria-hidden="true">×</span></button>
                            <div className="modal-body">
                                <form>
                                    <div className="form-group">
                                        <label>URI</label>
                                        <input type="url" className="form-control" />
                                    </div>
                                    <div className="form-group">
                                        <label>Description</label>
                                        <textarea rows={4} className="form-control" />
                                    </div>
                                    <div className="form-group">
                                        <label>Tags</label>
                                        <input type="url" className="form-control" />
                                    </div>
                                    <div className="row">
                                        <div className="col-md-6">
                                            <div className="form-group">
                                                <label>Capture Start Date</label>
                                                <input type="date" className="form-control" />
                                            </div>
                                        </div>
                                        <div className="col-md-6">
                                            <div className="form-group">
                                                <label>Capture End Date</label>
                                                <input type="date" className="form-control" />
                                            </div>
                                        </div>
                                    </div>
                                    <div className="row">
                                        <div className="col-md-6">
                                            <div className="form-group">
                                                <label>Area</label>
                                                <div className="select-group">
                                                    <input type="number" className="form-control" />
                                                    <select className="form-control">
                                                        <option>sq. mi</option>
                                                        <option>sq. km</option>
                                                    </select>
                                                </div>
                                            </div>
                                        </div>
                                        <div className="col-md-6">
                                            <div className="form-group">
                                                <label>Source Data Projection</label>
                                                <select className="form-control">
                                                </select>
                                            </div>
                                        </div>
                                    </div>
                                    <div className="row">
                                        <div className="col-md-6">
                                            <div className="form-group">
                                                <label>Total Images</label>
                                                <input type="number" readOnly className="form-control" />
                                            </div>
                                        </div>
                                    </div>
                                    <ul className="list-group">
                                        <li className="list-group-item">file_name.tiff <a className="pull-right" href="#"><i className="rf-icon-cancel text-danger" /></a></li>
                                        <li className="list-group-item">file_name_2.tiff <a className="pull-right" href="#"><i className="rf-icon-cancel text-danger" /></a></li>
                                        <li className="list-group-item">file_name_3.tiff <a className="pull-right" href="#"><i className="rf-icon-cancel text-danger" /></a></li>
                                    </ul>
                                    <div className="form-action">
                                        {/* <input type="submit" class="btn btn-secondary pull-right" value="Submit"> */}
                                        <button data-pane-target="#pane-3" data-toggle-pane className="btn btn-secondary pull-right">Submit</button>
                                        <button className="btn btn-link text-muted">Cancel</button>
                                    </div>
                                </form>
                            </div> {/* /.modal-body */}
                        </div> {/* /.modal-content */}
                    </div> {/* /.modal-dialog */}
                </div> {/* /#pane-2 */}
                <div className="pane animated fadeInDown" id="pane-3">
                    <div className="modal-dialog">
                        <div className="modal-content">
                            <button aria-label="Close" data-dismiss="modal" className="close" type="button"><span aria-hidden="true">×</span></button>
                            <div className="modal-body">
                                <form>
                                    <div className="row">
                                        <div className="col-md-8">
                                            <div className="form-group">
                                                <label>Source SRS</label>
                                                <input type="text" readOnly defaultValue="UTM Zone 18" className="form-control" />
                                            </div>
                                        </div>
                                        <div className="col-md-4">
                                            <label>&nbsp;</label>
                                            <button value="Change SRS" className="btn btn-default-outline btn-block">Change SRS</button>
                                        </div>
                                    </div>
                                    <div className="form-group">
                                        <label>Tile SRS</label>
                                        <select className="form-control">
                                            <option value>Web Mercator</option>
                                            <option value>UTM</option>
                                            <option value>WGS84 (EPSG: 4325)</option>
                                            <option value>EPSG/ESRI offline database</option>
                                        </select>
                                    </div>
                                    <hr />
                                    <h4>Mosaic Options</h4>
                                    <div className="form-group">
                                        <label>Tile Format</label>
                                        <select className="form-control">
                                            <option>JPEG</option>
                                            <option>Overlay PNG + optimisation (8 bit palette with alpha transparency)</option>
                                            <option>Overlay PNG format (32 bit RGBA with alpha transparency)</option>
                                            <option>Base map JPEG format (without transparency)</option>
                                            <option>Base map PNG format + optimisation (8 bit palette with alpha transparency)</option>
                                            <option>Base map PNG format (24 bit RGB without transparency)</option>
                                        </select>
                                    </div>
                                    <div className="form-group">
                                        <label>Resampling</label>
                                        <select className="form-control">
                                            <option value>Bilinear</option>
                                            <option value>Cubic</option>
                                            <option value>Cubic B-Spline</option>
                                            <option value>Average</option>
                                            <option value>Mode</option>
                                            <option value>Nearest Neighbor</option>
                                        </select>
                                    </div>
                                    <div className="form-group">
                                        <label>Transparency Settings</label><br />
                                        <div className="radio">
                                            <input type="radio" defaultChecked name="transparency-setting" />
                                            <label>Default</label>
                                        </div>
                                        <br />
                                        <div className="radio">
                                            <input type="radio" name="transparency-setting" />
                                            <label>Color to Alpha</label>
                                        </div>
                                        <input type="color" />
                                    </div>
                                    <div className="form-group">
                                        <label>Tiling Scheme</label><br />
                                        <div className="radio">
                                            <input type="radio" defaultChecked name="tiling-scheme" />
                                            <label>OGC WMTS / OpenStreetMap / Google XYZ (top-left origin)</label>
                                        </div>
                                        <br />
                                        <div className="radio">
                                            <input type="radio" name="tiling-scheme" />
                                            <label>OSGEO TMS (bottom-left origin)</label>
                                        </div>
                                    </div>
                                    <hr />
                                    <div className="form-group">
                                        <label>Image Upload Progress</label>
                                        <div className="progress">
                                            <div style={{width: '60%'}} aria-valuemax={100} aria-valuemin={0} aria-valuenow={60} role="progressbar" className="progress-bar">
                                                <span className="sr-only">60% Complete</span>
                                            </div>
                                        </div>
                                        <em>Mosaic won't begin until upload has completed</em>
                                    </div>
                                    <div className="form-action">
                                        <input type="submit" defaultValue="Submit" className="btn btn-secondary pull-right" />
                                        <button className="btn btn-link text-muted">Cancel</button>
                                    </div>
                                </form>
                            </div> {/* /.modal-body */}
                        </div> {/* /.modal-content */}
                    </div> {/* /.modal-dialog */}
                </div> {/* /#pane-3 */}
                {/* /import modal */}
            </div>
        );
    }
});

module.exports = UploadModal;
