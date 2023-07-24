$(document).ready(function(){

    var BATCH_LANGUAGES = ["java", "xml", "both"];
    var $xmlButton = $("#xmlButton");
    var $javaButton = $("#javaButton");
    var $bothButton = $("#bothButton");

    var $xmlContent = $("*.xmlContent");
    var $xmlContentAll = $("*.xmlContent > *");

    var $javaContent = $("*.javaContent");
    var $javaContentAll = $("*.javaContent > *");

    // Initial cookie handler. This part remembers the
    // reader's choice and sets the toggle accordingly.
    var lang = window.localStorage.getItem("docToggle");
    if (BATCH_LANGUAGES.indexOf(lang) === -1) {
        lang = "java";
        $javaButton.prop("checked", true);
        setJava();
    } else {
        if (lang === "xml") {
            $xmlButton.prop("checked", true);
            setXml();
        }
        if (lang === "java") {
            $javaButton.prop("checked", true);
            setJava();
        }
        if (lang === "both") {
            $javaButton.prop("checked", true);
            setBoth();
        }
    }

    // Click handlers
    $xmlButton.on("click", function() {
        setXml();
    });
    $javaButton.on("click", function() {
        setJava();
    });
    $bothButton.on("click", function() {
        setBoth();
    });

    // Functions to do the work of handling the reader's choice, whether through a click
    // or through a cookie. 3652 days is 10 years, give or take a leap day.
    function setXml() {
        $xmlContent.show();
        $javaContent.hide();
        $javaContentAll.addClass("js-toc-ignore");
        $xmlContentAll.removeClass("js-toc-ignore");
        window.dispatchEvent(new Event("tocRefresh"));
        window.localStorage.setItem('docToggle', 'xml');
    }

    function setJava() {
        $javaContent.show();
        $xmlContent.hide();
        $xmlContentAll.addClass("js-toc-ignore");
        $javaContentAll.removeClass("js-toc-ignore");
        window.dispatchEvent(new Event("tocRefresh"));
        window.localStorage.setItem('docToggle', 'java');
    }

    function setBoth() {
        $javaContent.show();
        $xmlContent.show();
        $javaContentAll.removeClass("js-toc-ignore");
        $xmlContentAll.removeClass("js-toc-ignore");
        window.dispatchEvent(new Event("tocRefresh"));
        window.localStorage.setItem('docToggle', 'both');
    }

});
