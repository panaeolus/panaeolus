var tokens = [ ['{','}'] , ['[',']'] , ['(',')'] ];


// *** Check if character is an opening bracket ***
function isOpenParenthesis(parenthesisChar) {
    for (var j = 0; j < tokens.length; j++) {
        if (tokens[j][0] === parenthesisChar) {
            return true;
        }
    }
    return false;
}

// *** Check if opening bracket matches closing bracket ***
function matches(topOfStack, closedParenthesis) {
    for (var k = 0; k < tokens.length; k++) {
        if (tokens[k][0] === topOfStack && tokens[k][1] === closedParenthesis) {
            return true;
        }
    }
    return false;
}

// *** Checks if item is any sort of paranthesis ***
function isParanthesis(char) {
    var str = '{}[]()';
    if (str.indexOf(char) > -1) {
        return true;
    } else {
        return false;
    }
}


// function indexToPosition(index, currentText) {
//     var lines = currentText.split("\n");
//     var newlineLength = currentText.split(/\r\n|\r|\n/).length;
//     for (var i = 0, l = lines.length; i < l; i++) {
//         index -= lines[i].length + newlineLength;
//         if (index < 0)
//             return {row: i, column: index + lines[i].length + newlineLength};
//     }
//     return {row: l-1, column: index + lines[l-1].length + newlineLength};
// };
//

function sexpAtPoint(inputStr, cursorPos) {
    // var inputStr =

    var expression = inputStr.split('');
    var stack = [];
    var returnValue = true;

    var cursorPos = cursorPos;
    var sexpBegin = 0;
    var sexpEnd = 0;

    for (var i = 0; i < expression.length; i++) {
        if ((cursorPos >= sexpBegin) &&
	    (cursorPos <= sexpEnd) &&
	    (sexpEnd != 0)) {
            return {
                startIndex: sexpBegin,
                endIndex: sexpEnd,
                // pointACoord: indexToPosition(sexpBegin, inputStr),
                // pointBCoord: indexToPosition(sexpEnd, inputStr),
            } [sexpBegin, sexpEnd];
        } else {
            if (isParanthesis(expression[i])) {
	        if (isOpenParenthesis(expression[i])) {
	            if (stack.length === 0) {
	                sexpBegin = i;
	            }
	            stack.push(expression[i]);
	        } else {
                    if (stack.length === 0) {
	                console.log("Warning: unbalanced parenthesis!\n")
                        // return false;
                    }
	            if (stack.length === 1) {
                        sexpEnd = i + 1;
                    }
                    if (!matches(stack[stack.length - 1], expression[i])) {
	                console.log("Warning: unbalanced parenthesis!\n")
                        // return false;
                    }
                    stack.pop();
	        }
            }
        }
    }

    if (stack.length != 0) {
        console.log("Warning: unbalanced parenthesis!\n")
    }

    if ((cursorPos >= sexpBegin) &&
        (cursorPos <= sexpEnd) &&
        (sexpEnd != 0)) {
        return {
            startIndex: sexpBegin,
            endIndex: sexpEnd,
            // pointACoord: indexToPosition(sexpBegin, inputStr),
            // pointBCoord: indexToPosition(sexpEnd, inputStr),
        }
    } else {
        return false;
    }
}

module.exports = sexpAtPoint;
