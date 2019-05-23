ace.define("ace/theme/cyberpunk",["require","exports","module","ace/lib/dom"], function(acequire, exports, module) {

    // text-shadow:\
    // 0 0 5px #d3d3d3,\
    // 0 0 10px #d3d3d3,\
    // 0 0 20px #d3d3d3,\
    // 0 0 40px #d3d3d3,\
    // 0 0 80px #d3d3d3,\
    // 0 0 90px #d3d3d3,\
    // 0 0 100px #d3d3d3,\
    // 0 0 150px #d3d3d3;\
    // }\
    //
    exports.isDark = true;
    exports.cssClass = "ace-cyberpunk";
    exports.cssText = "\
.ace-cyberpunk .ace_gutter {\
color: #333333\
}\
.ace-cyberpunk  {\
background: #000;\
color: #d3d3d3;\
}\
.ace-cyberpunk .ace_keyword {\
font-weight: bold;\
color: #4c83ff;\
}\
.ace-cyberpunk .ace_string {\
color: #61CE3C;\
}\
.ace-cyberpunk .ace_variable.ace_class {\
color: #ff1493;\
}\
.ace-cyberpunk .ace_constant.ace_numeric {\
color: d3d3d3;\
}\
.ace-cyberpunk .ace_constant.ace_buildin {\
color: #96CBFE;\
}\
.ace-cyberpunk .ace_support.ace_function {\
color: #0086B3;\
}\
.ace-cyberpunk .ace_comment {\
color: #8B8989;\
font-style: italic;\
}\
.ace-cyberpunk .ace_variable.ace_language  {\
color: #0086B3;\
}\
.ace-cyberpunk .ace_paren {\
font-weight: bold;\
color: #cd1076;\
}\
.ace-cyberpunk .ace_boolean {\
color: #0086B3;\
font-weight: bold;\
}\
.ace-cyberpunk .ace_string.ace_regexp {\
color: #9fc59f;\
font-weight: normal;\
}\
.ace-cyberpunk .ace_variable.ace_instance {\
color: teal;\
}\
.ace-cyberpunk .ace_constant.ace_language {\
font-weight: bold;\
}\
.ace-cyberpunk .ace_cursor {\
color: #dcdccc;\
}\
.ace-cyberpunk.ace_focus .ace_marker-layer .ace_active-line {\
background-color: #333333;\
}\
.ace-cyberpunk .ace_marker-layer .ace_active-line {\
background-color: #333333;\
}\
.ace-cyberpunk .ace_marker-layer .ace_selection {\
background-color: #7F073F;\
}\
.ace-cyberpunk.ace_multiselect .ace_selection.ace_start {\
box-shadow: 0 0 3px 0px white;\
}\
.ace-cyberpunk.ace_nobold .ace_line > span {\
font-weight: normal !important;\
}\
.ace-cyberpunk .ace_marker-layer .ace_step {\
background: rgb(252, 255, 0);\
}\
.ace-cyberpunk .ace_marker-layer .ace_stack {\
background: rgb(164, 229, 101);\
}\
.ace-cyberpunk .ace_marker-layer .ace_bracket {\
margin: -1px 0 0 -1px;\
border: 1px solid rgb(192, 192, 192);\
color: #cd1076;\
}\
.ace-cyberpunk .ace_gutter-active-line {\
background-color : rgba(0, 0, 100, 0.07);\
}\
.ace-cyberpunk .ace_marker-layer .ace_selected-word {\
background: rgb(250, 250, 255);\
border: 1px solid rgb(200, 200, 250);\
}\
.ace-cyberpunk .ace_invisible {\
color: #7F073F\
}\
.ace-cyberpunk .ace_print-margin {\
width: 100px;\
background: #e8e8e8;\
}\
.ace-cyberpunk .ace_indent-guide {\
background: url(\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAACCAYAAACZgbYnAAAAE0lEQVQImWP4////f4bLly//BwAmVgd1/w11/gAAAABJRU5ErkJggg==\") right repeat-y;\
}";

    var dom = acequire("../lib/dom");
    dom.importCssString(exports.cssText, exports.cssClass);
});
