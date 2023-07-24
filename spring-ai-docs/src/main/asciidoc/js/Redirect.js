$(document).ready(function(){

    redirect();

    function redirect() {
        var anchorMap = {
            "#domain": "#domainLanguageOfBatch",
            "#domainJob": "#job",
            "#domainJobInstance": "#jobinstance",
            "#domainJobParameters": "#jobparameters",
            "#domainJobExecution": "#jobexecution",
            "#d5e455": "#jobexecution",
            "#d5e497": "#jobexecution",
            "#d5e507": "#jobexecution",
            "#d5e523": "#jobexecution",
            "#d5e550": "#jobexecution",
            "#d5e563": "#jobexecution",
            "#d5e591": "#jobexecution",
            "#domainStep": "#step",
            "#domainStepExecution": "#stepexecution",
            "#d5e655": "#stepexecution",
            "#domainExecutionContext": "#executioncontext",
            "#d5e721": "#executioncontext",
            "#d5e731": "#executioncontext",
            "#d5e745": "#executioncontext",
            "#d5e761": "#executioncontext",
            "#d5e779": "#executioncontext",
            "#domainJobRepository": "#jobrepository",
            "#domainJobLauncher": "#joblauncher",
            "#domainItemReader": "#item-reader",
            "#domainItemWriter": "#item-writer",
            "#domainItemProcessor": "#item-processor",
            "#domainBatchNamespace": "#batch-namespace",
            "#d5e970": "#jobparametersvalidator",
            "#d5e1130": "#commandLineJobRunner",
            "#d5e1232": "#jobregistry",
            "#d5e1237": "#jobregistrybeanpostprocessor",
            "#d5e1242": "#automaticjobregistrar",
            "#d5e1320": "#aborting-a-job",
            "#filiteringRecords": "#filteringRecords",
            "#d5e2247": "#flatFileItemReader",
            "#d5e2769": "#JdbcCursorItemReaderProperties",
            "#stepExecutionSplitter": "#partitioner",
            "#d5e3182": "#bindingInputDataToSteps",
            "#d5e3241": "#repeatStatus",
            "#d5e3531": "#testing-step-scoped-components",
            "#patterns": "#commonPatterns",
            "#d5e3959": "#item-based-processing",
            "#d5e3969": "#custom-checkpointing",
            "#available-attributes-of-the-job-launching-gateway": "#availableAttributesOfTheJobLaunchingGateway",
            "#d5e4425": "#itemReadersAppendix",
            "#d5e4494": "#itemWritersAppendix",
            "#d5e4788": "#recommendationsForIndexingMetaDataTables"
        };
        var baseUrl = window.location.origin + window.location.pathname;
        var anchor = window.location.hash;
        if (anchor && anchorMap[anchor] != null) {
            window.location.replace(baseUrl + anchorMap[anchor]);
        }
    }

});
