#!/usr/bin/env node

var path = require('path')
var fs = require('fs')
var postcss = require('postcss')
var modules = require('postcss-modules')

var args = process.argv
var infile = args[2]
var outfile = args[3]
var hash = args[4]

function getJSONFromCssModules(cssfileName, json) {
    process.stdout.write('~json~' + JSON.stringify(json) + '~json~')
}

function generateScopedName(name, filename, css) {
    return `_${name}_${hash}`
}

fs.readFile(infile, function (err, data) {
    if (err) {
        console.log(err)
        process.exit(1)
    }

    var res = postcss([
        modules({
            getJSON: getJSONFromCssModules
            , generateScopedName: generateScopedName
         })
    ])
    .process(data, { from: infile, to: outfile })
    .then(function (result) {
        process.stdout.write('~css~' + result.css + '~css~')
    })
})




