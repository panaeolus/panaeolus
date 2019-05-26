ace.define("ace/mode/dynamic", ["require","exports","module","ace/lib/oop","ace/mode/text_highlight_rules", "ace/mode/clojure_highlight_rules"], function(require, exports, module) {
    "use strict";

    var oop = require("ace/lib/oop");

    var TextHighlightRules = require("./text_highlight_rules").TextHighlightRules;
    var ClojureHighlightRules = require("ace/mode/clojure_highlight_rules").ClojureHighlightRules;
    var DynHighlightRules = function() {
        ClojureHighlightRules.call(this);

        this.keywordRule = {
            regex : "\\w+",
            onMatch : function() {return "text"}
        }

        this.setKeywords = function(kwMap) {
            this.keywordRule.onMatch = this.createKeywordMapper(kwMap, "text", true)
        }


        this.$rules = {
            "start": this.$rules["start"].concat([this.keywordRule]),
            "string": this.$rules["string"],
        };


        this.normalizeRules()
    };

    oop.inherits(DynHighlightRules, TextHighlightRules);

    exports.DynHighlightRules = DynHighlightRules;

});
