require 'xcodeproj'

project_path = File.join(__dir__, 'SignalSpike.xcodeproj')
project = File.exist?(project_path) ? Xcodeproj::Project.open(project_path) : Xcodeproj::Project.new(project_path)
app = project.targets.find { |t| t.name == 'SignalSpike' } || project.new_target(:application, 'SignalSpike', :ios, '15.0')
tests = project.targets.find { |t| t.name == 'SignalSpikeTests' } || project.new_target(:unit_test_bundle, 'SignalSpikeTests', :ios, '15.0')
tests.add_dependency(app) unless tests.dependencies.any? { |d| d.target == app }

{app => ['AppDelegate.swift', '../../../iosApp/SecureChat/ProtectedVault.swift', '../../../iosApp/SecureChat/DurableStore.swift', '../../../iosApp/SecureChat/SecureChatEngine.swift', '../../../iosApp/SecureChat/AttachmentVault.swift'], tests => Dir.glob(File.join(__dir__, 'Tests', '*.swift')).map { |p| p.delete_prefix(__dir__ + '/') }}.each do |target, files|
  files.each do |file|
    unless target.source_build_phase.files_references.any? { |ref| ref.path == file }
      reference = project.main_group.files.find { |ref| ref.path == file } || project.main_group.new_file(file)
      target.source_build_phase.add_file_reference(reference)
    end
  end
  target.build_configurations.each do |config|
    config.build_settings.merge!(
      'SWIFT_VERSION' => '5.0',
      'ENABLE_TESTABILITY' => 'YES',
      'GENERATE_INFOPLIST_FILE' => 'YES',
      'PRODUCT_BUNDLE_IDENTIFIER' => "com.srisu.evaluation.#{target.name}",
      'CODE_SIGN_STYLE' => 'Automatic',
      'SWIFT_ENABLE_EXPLICIT_MODULES' => 'NO',
      'TARGETED_DEVICE_FAMILY' => '1,2',
      'LD_RUNPATH_SEARCH_PATHS' => ['$(inherited)', '@executable_path/Frameworks', '@loader_path/Frameworks']
    )
  end
end
tests.build_configurations.each do |config|
  config.build_settings['TEST_HOST'] = '$(BUILT_PRODUCTS_DIR)/SignalSpike.app/SignalSpike'
  config.build_settings['BUNDLE_LOADER'] = '$(TEST_HOST)'
end
project.save
scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(app)
scheme.add_test_target(tests)
scheme.save_as(project_path, 'SignalSpike', true)
