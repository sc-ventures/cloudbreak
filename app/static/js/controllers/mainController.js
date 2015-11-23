'use strict';

var log = log4javascript.getLogger("mainController-logger");
var $jq = jQuery.noConflict();

angular.module('uluwatuControllers')
    .run(['$rootScope', 'PlatformParameters',
        function($rootScope, PlatformParameters) {
            PlatformParameters.get().$promise.then(function(success) {
                $rootScope.params = {};
                $rootScope.params.regions = success.regions.regions;
                $rootScope.params.defaultRegions = success.regions.defaultRegions;
                $rootScope.params.zones = success.regions.availabilityZones;
                $rootScope.params.diskTypes = success.disks.diskTypes;
                $rootScope.params.defaultDisks = success.disks.defaultDisks;
            }, function(error) {
                $rootScope.params.regions = {};
                $rootScope.params.defaultRegions = {};
                $rootScope.params.zones = {};
                $rootScope.params.diskTypes = {};
                $rootScope.params.defaultDisks = {};
            });
        }
    ])
    .controller('mainController', ['$scope', '$rootScope', '$filter', '$interval', 'PlatformParameters',
        function($scope, $rootScope, $filter, $interval, PlatformParameters) {

            $rootScope.fileReadAvailable = window.File && window.FileReader && window.FileList && window.Blob ? true : false;

            $scope.showManagement = true;
            $scope.showAccountPanel = false;

            $scope.activateManagement = function() {
                $scope.showManagement = true;
                $scope.showAccountPanel = false;
            }

            $scope.activateAccountPanel = function() {
                $scope.showManagement = false;
                $scope.showAccountPanel = true;
            }

            $rootScope.config = {
                regionDisplayNames: {
                    get: function(provider, nameId) {
                        if (provider !== undefined && nameId !== undefined && this[provider] !== undefined && this[provider][nameId] !== undefined) {
                            return this[provider][nameId].value;
                        }
                        return nameId;
                    },
                    getById: function(nameId) {
                        var result = nameId,
                            that = this;
                        angular.forEach(that, function(value, key) {
                            if (typeof value !== "function" && that.get(key, nameId) !== nameId) {
                                result = that.get(key, nameId);
                            }
                        });
                        return result;
                    },
                    'AWS': {
                        'us-east-1': {
                            value: 'US East(N. Virginia)'
                        },
                        'us-west-1': {
                            value: 'US West (N. California)'
                        },
                        'us-west-2': {
                            value: 'US West (Oregon)'
                        },
                        'eu-west-1': {
                            value: 'EU (Ireland)'
                        },
                        'eu-central-1': {
                            value: 'EU (Frankfurt)'
                        },
                        'ap-southeast-1': {
                            value: 'Asia Pacific (Singapore)'
                        },
                        'ap-southeast-2': {
                            value: 'Asia Pacific (Sydney)'
                        },
                        'ap-northeast-1': {
                            value: 'Asia Pacific (Tokyo)'
                        },
                        'sa-east-1': {
                            value: 'South America (São Paulo)'
                        },
                    },
                    'GCP': {
                        'us-central1': {
                            value: "Central US"
                        },
                        'europe-west1': {
                            value: "Western Europe"
                        },
                        'asia-east1': {
                            value: "East Asia"
                        },
                        'us-east1': {
                            value: "Eastern US"
                        }
                    }
                },
                diskDisplayNames: {
                    get: function(provider, nameId) {
                        if (provider !== undefined && nameId !== undefined && this[provider] !== undefined && this[provider][nameId] !== undefined) {
                            return this[provider][nameId];
                        }
                        return nameId;
                    },
                    'GCP': {
                        'pd-standard': 'Standard persistent disks (HDD)',
                        'pd-ssd': 'Solid-state persistent disks (SSD)'
                    },
                    'AWS': {
                        'ephemeral': 'Ephemeral',
                        'standard': 'Magnetic',
                        'gp2': 'General Purpose (SSD)'
                    }
                },
                'GCP': {
                    gcpInstanceTypes: [{
                        key: 'N1_STANDARD_1',
                        value: 'n1-standard-1',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_STANDARD_2',
                        value: 'n1-standard-2',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_STANDARD_4',
                        value: 'n1-standard-4',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_STANDARD_8',
                        value: 'n1-standard-8',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_STANDARD_16',
                        value: 'n1-standard-16',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_HIGHMEM_2',
                        value: 'n1-highmem-2',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_HIGHMEM_4',
                        value: 'n1-highmem-4',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_HIGHMEM_8',
                        value: 'n1-highmem-8',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_HIGHMEM_16',
                        value: 'n1-highmem-16',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_HIGHCPU_2',
                        value: 'n1-highcpu-2',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_HIGHCPU_4',
                        value: 'n1-highcpu-4',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_HIGHCPU_8',
                        value: 'n1-highcpu-8',
                        cloud: 'GCP'
                    }, {
                        key: 'N1_HIGHCPU_16',
                        value: 'n1-highcpu-16',
                        cloud: 'GCP'
                    }],
                    gcpDiskTypes: [{
                        key: 'HDD',
                        value: 'Magnetic'
                    }, {
                        key: 'SSD',
                        value: 'SSD'
                    }]
                },
                'AZURE': {
                    azureRegions: [{
                        key: 'BRAZIL_SOUTH',
                        value: 'Brazil South',
                        cloud: 'AZURE'
                    }, {
                        key: 'EAST_ASIA',
                        value: 'East Asia',
                        cloud: 'AZURE'
                    }, {
                        key: 'EAST_US',
                        value: 'East US',
                        cloud: 'AZURE'
                    }, {
                        key: 'CENTRAL_US',
                        value: 'Central US',
                        cloud: 'AZURE'
                    }, {
                        key: 'NORTH_EUROPE',
                        value: 'North Europe',
                        cloud: 'AZURE'
                    }, {
                        key: 'SOUTH_CENTRAL_US',
                        value: 'South Central US'
                    }, {
                        key: 'NORTH_CENTRAL_US',
                        value: 'North Central US',
                        cloud: 'AZURE'
                    }, {
                        key: 'EAST_US_2',
                        value: 'East US 2',
                        cloud: 'AZURE'
                    }, {
                        key: 'JAPAN_EAST',
                        value: 'Japan East',
                        cloud: 'AZURE'
                    }, {
                        key: 'JAPAN_WEST',
                        value: 'Japan West',
                        cloud: 'AZURE'
                    }, {
                        key: 'SOUTHEAST_ASIA',
                        value: 'Southeast Asia',
                        cloud: 'AZURE'
                    }, {
                        key: 'WEST_US',
                        value: 'West US',
                        cloud: 'AZURE'
                    }, {
                        key: 'WEST_EUROPE',
                        value: 'West EU',
                        cloud: 'AZURE'
                    }],
                    azureVmTypes: [{
                        key: 'A5',
                        value: 'Standard A5',
                        cloud: 'AZURE'
                    }, {
                        key: 'A6',
                        value: 'Standard A6',
                        cloud: 'AZURE'
                    }, {
                        key: 'A7',
                        value: 'Standard A7',
                        cloud: 'AZURE'
                    }, {
                        key: 'A8',
                        value: 'Standard A8',
                        cloud: 'AZURE'
                    }, {
                        key: 'A9',
                        value: 'Standard A9',
                        cloud: 'AZURE'
                    }, {
                        key: 'A10',
                        value: 'Standard A10',
                        cloud: 'AZURE'
                    }, {
                        key: 'A11',
                        value: 'Standard A11',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D1',
                        value: 'Standard D1',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D2',
                        value: 'Standard D2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D3',
                        value: 'Standard D3',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D4',
                        value: 'Standard D4',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D11',
                        value: 'Standard D11',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D12',
                        value: 'Standard D12',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D13',
                        value: 'Standard D13',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D14',
                        value: 'Standard D14',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_G1',
                        value: 'Standard G1',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_G2',
                        value: 'Standard G2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_G3',
                        value: 'Standard G3',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_G4',
                        value: 'Standard G4',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_G5',
                        value: 'Standard G5',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D1_V2',
                        value: 'Standard D1 v2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D2_V2',
                        value: 'Standard D2 v2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D3_V2',
                        value: 'Standard D3 v2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D4_V2',
                        value: 'Standard D4 v2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D5_V2',
                        value: 'Standard D5 v2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D11_V2',
                        value: 'Standard D11 v2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D12_V2',
                        value: 'Standard D12 v2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D13_V2',
                        value: 'Standard D13 v2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D14_V2',
                        value: 'Standard D14 v2',
                        cloud: 'AZURE'
                    }]
                },
                'AZURE_RM': {
                    azureVmTypes: [{
                        key: 'A5',
                        value: 'A5',
                        cloud: 'AZURE'
                    }, {
                        key: 'A6',
                        value: 'A6',
                        cloud: 'AZURE'
                    }, {
                        key: 'A7',
                        value: 'A7',
                        cloud: 'AZURE'
                    }, {
                        key: 'A8',
                        value: 'A8',
                        cloud: 'AZURE'
                    }, {
                        key: 'A9',
                        value: 'A9',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D1',
                        value: 'D1',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D2',
                        value: 'D2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D3',
                        value: 'D3',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D4',
                        value: 'D4',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_G1',
                        value: 'G1',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_G2',
                        value: 'G2',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_G3',
                        value: 'G3',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_G4',
                        value: 'G4',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_G5',
                        value: 'G5',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D11',
                        value: 'D11',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D12',
                        value: 'D12',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D13',
                        value: 'D13',
                        cloud: 'AZURE'
                    }, {
                        key: 'STANDARD_D14',
                        value: 'D14',
                        cloud: 'AZURE'
                    }]
                },
                'AWS': {
                    instanceType: [{
                        key: 'C3Large',
                        value: 'C3Large',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '16 GB SSD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'C3Xlarge',
                        value: 'C3Xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '40 GB SSD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'C32xlarge',
                        value: 'C32xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '80 GB SSD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'C34xlarge',
                        value: 'C34xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '160 GB SSD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'C38xlarge',
                        value: 'C38xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '320 GB SSD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'Cc28xlarge',
                        value: 'Cc28xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '840 GB STANDARD',
                        maxEphemeralVolumeCount: 4
                    }, {
                        key: 'Cg14xlarge',
                        value: 'Cg14xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '840 GB STANDARD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'Cr18xlarge',
                        value: 'Cr18xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '120 GB SSD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'G22xlarge',
                        value: 'G22xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '60 GB SSD',
                        maxEphemeralVolumeCount: 1
                    }, {
                        key: 'Hi14xlarge',
                        value: 'Hi14xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '1024 GB SSD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'Hs18xlarge',
                        value: 'Hs18xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '2048 GB STANDARD',
                        maxEphemeralVolumeCount: 24
                    }, {
                        key: 'I2Xlarge',
                        value: 'I2Xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '800 GB SSD',
                        maxEphemeralVolumeCount: 1
                    }, {
                        key: 'I22xlarge',
                        value: 'I22xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '800 GB SSD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'I24xlarge',
                        value: 'I24xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '800 GB SSD',
                        maxEphemeralVolumeCount: 4
                    }, {
                        key: 'I28xlarge',
                        value: 'I28xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '800 GB SSD',
                        maxEphemeralVolumeCount: 8
                    }, {
                        key: 'M3Medium',
                        value: 'M3Medium',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '4 GB SSD',
                        maxEphemeralVolumeCount: 1
                    }, {
                        key: 'M3Large',
                        value: 'M3Large',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '32 GB SSD',
                        maxEphemeralVolumeCount: 1
                    }, {
                        key: 'M3Xlarge',
                        value: 'M3Xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '40 GB SSD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'M32xlarge',
                        value: 'M32xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '80 GB SSD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'R3Large',
                        value: 'R3Large',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '32 GB SSD',
                        maxEphemeralVolumeCount: 1
                    }, {
                        key: 'R3Xlarge',
                        value: 'R3Xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '80 GB SSD',
                        maxEphemeralVolumeCount: 1
                    }, {
                        key: 'R32xlarge',
                        value: 'R32xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '160 GB SSD',
                        maxEphemeralVolumeCount: 1
                    }, {
                        key: 'R34xlarge',
                        value: 'R34xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '320 GB SSD',
                        maxEphemeralVolumeCount: 1
                    }, {
                        key: 'R38xlarge',
                        value: 'R38xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '320 GB SSD',
                        maxEphemeralVolumeCount: 2
                    }, {
                        key: 'D2Xlarge',
                        value: 'D2Xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '2000 GB STANDARD',
                        maxEphemeralVolumeCount: 3
                    }, {
                        key: 'D22xlarge',
                        value: 'D22xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '2000 GB STANDARD',
                        maxEphemeralVolumeCount: 6
                    }, {
                        key: 'D24xlarge',
                        value: 'D24xlarge',
                        cloud: 'AWS',
                        ephemeralVolumeSize: '2000 GB STANDARD',
                        maxEphemeralVolumeCount: 12
                    }]

                },
                'RECIPE_TYPE': {
                    content_types: [{
                        key: 'SCRIPT',
                        value: 'SCRIPT'
                    }, {
                        key: 'FILE',
                        value: 'FILE'
                    }, {
                        key: 'URL',
                        value: 'URL'
                    }],
                    execution_types: [{
                        key: 'ALL_NODES',
                        value: 'ALL_NODES'
                    }, {
                        key: 'ONE_NODE',
                        value: 'ONE_NODE'
                    }]
                },
                'BLUEPRINT_TYPE': [{
                    key: 'TEXT',
                    value: 'TEXT'
                }, {
                    key: 'FILE',
                    value: 'FILE'
                }, {
                    key: 'URL',
                    value: 'URL'
                }],
                'EVENT_TYPE': {
                    "REQUESTED": "requested",
                    "CREATE_IN_PROGRESS": "create in progress",
                    "AVAILABLE": "available",
                    "UPDATE_IN_PROGRESS": "update in progress",
                    "UPDATE_FAILED": "update failed",
                    "CREATE_FAILED": "create failed",
                    "DELETE_IN_PROGRESS": "delete in progress",
                    "DELETE_FAILED": "delete failed",
                    "DELETE_COMPLETED": "delete completed",
                    "STOPPED": "stopped",
                    "STOP_REQUESTED": "stop requested",
                    "START_REQUESTED": "start requested",
                    "STOP_IN_PROGRESS": "stop in progress",
                    "START_IN_PROGRESS": "start in progress",
                    "START_FAILED": "start failed",
                    "STOP_FAILED": "stop failed",
                    "BILLING_STARTED": "billing started",
                    "BILLING_STOPPED": "billing stopped",
                    "WAIT_FOR_SYNC": "unknown",
                },
                'EVENT_CLASS': {
                    "REQUESTED": "has-warning",
                    "CREATE_IN_PROGRESS": "has-warning",
                    "AVAILABLE": "has-success",
                    "UPDATE_IN_PROGRESS": "has-warning",
                    "UPDATE_FAILED": "has-error",
                    "CREATE_FAILED": "has-error",
                    "DELETE_IN_PROGRESS": "has-warning",
                    "DELETE_FAILED": "has-error",
                    "DELETE_COMPLETED": "has-success",
                    "STOPPED": "has-success",
                    "STOP_REQUESTED": "has-warning",
                    "START_REQUESTED": "has-warning",
                    "STOP_IN_PROGRESS": "has-warning",
                    "START_IN_PROGRESS": "has-warning",
                    "START_FAILED": "has-error",
                    "STOP_FAILED": "has-error",
                    "BILLING_STARTED": "has-success",
                    "BILLING_STOPPED": "has-success",
                    "WAIT_FOR_SYNC": "has-error"
                },
                'TIME_ZONES': [{
                    key: 'Etc/GMT+1',
                    value: 'GMT-1'
                }, {
                    key: 'Etc/GMT+2',
                    value: 'GMT-2'
                }, {
                    key: 'Etc/GMT+3',
                    value: 'GMT-3'
                }, {
                    key: 'Etc/GMT+4',
                    value: 'GMT-4'
                }, {
                    key: 'Etc/GMT+5',
                    value: 'GMT-5'
                }, {
                    key: 'Etc/GMT+6',
                    value: 'GMT-6'
                }, {
                    key: 'Etc/GMT+7',
                    value: 'GMT-7'
                }, {
                    key: 'Etc/GMT+8',
                    value: 'GMT-8'
                }, {
                    key: 'Etc/GMT+9',
                    value: 'GMT-9'
                }, {
                    key: 'Etc/GMT+10',
                    value: 'GMT-10'
                }, {
                    key: 'Etc/GMT+11',
                    value: 'GMT-11'
                }, {
                    key: 'Etc/GMT+12',
                    value: 'GMT-12'
                }, {
                    key: 'Etc/GMT',
                    value: 'GMT/UTC'
                }, {
                    key: 'Etc/GMT-1',
                    value: 'GMT+1'
                }, {
                    key: 'Etc/GMT-2',
                    value: 'GMT+2'
                }, {
                    key: 'Etc/GMT-3',
                    value: 'GMT+3'
                }, {
                    key: 'Etc/GMT-4',
                    value: 'GMT+4'
                }, {
                    key: 'Etc/GMT-5',
                    value: 'GMT+5'
                }, {
                    key: 'Etc/GMT-6',
                    value: 'GMT+6'
                }, {
                    key: 'Etc/GMT-7',
                    value: 'GMT+7'
                }, {
                    key: 'Etc/GMT-8',
                    value: 'GMT+8'
                }, {
                    key: 'Etc/GMT-9',
                    value: 'GMT+9'
                }, {
                    key: 'Etc/GMT-10',
                    value: 'GMT+10'
                }, {
                    key: 'Etc/GMT-11',
                    value: 'GMT+11'
                }, {
                    key: 'Etc/GMT-12',
                    value: 'GMT+12'
                }]
            }


        }
    ]);