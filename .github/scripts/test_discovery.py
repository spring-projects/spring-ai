#!/usr/bin/env python3
"""
GitHub Actions compatible test discovery script
Outputs comma-separated list of affected modules for CI builds

This script is designed to work in GitHub Actions CI environment where:
- The repository is already checked out
- We need to determine which Maven modules are affected by the changes
- Output should be a simple comma-separated list for use with mvn -pl parameter
"""

import sys
import os
import subprocess
from pathlib import Path
from typing import List, Optional, Set

class CITestDiscovery:
    """Test discovery for CI environments (GitHub Actions)"""
    
    def __init__(self, repo_root: Path = Path(".")):
        self.repo_root = Path(repo_root)
        self._last_git_command = None
        
    def modules_from_diff(self, base_ref: Optional[str] = None, verbose: bool = False) -> str:
        """Get affected modules from git diff (for GitHub Actions)"""
        try:
            changed_files = self._get_changed_files(base_ref)
            affected_modules = self._discover_affected_modules(changed_files)
            
            # Verbose logging to stderr
            if verbose:
                detected_base = base_ref if base_ref else self._detect_default_base()
                print(f"Detected base ref: {detected_base}", file=sys.stderr)
                print(f"Git diff strategy used: {self._get_last_git_command()}", file=sys.stderr)
                print(f"Changed files ({len(changed_files)}):", file=sys.stderr)
                for file in changed_files[:10]:  # Limit to first 10 for readability
                    print(f"  - {file}", file=sys.stderr)
                if len(changed_files) > 10:
                    print(f"  ... and {len(changed_files) - 10} more files", file=sys.stderr)
                result_str = ",".join(affected_modules) if affected_modules else "<none>"
                print(f"Final module list: {result_str}", file=sys.stderr)
            
            if not affected_modules:
                # Return empty string for no modules (GitHub Actions will skip module-specific build)
                return ""
            else:
                # Return comma-separated list for mvn -pl parameter
                return ",".join(affected_modules)
                
        except Exception as e:
            # In CI, print error to stderr and return empty string to fail gracefully
            print(f"Error in test discovery: {e}", file=sys.stderr)
            print("Falling back to full build due to test discovery failure", file=sys.stderr)
            return ""
    
    def _get_changed_files(self, base_ref: Optional[str] = None) -> List[str]:
        """Get changed files from git diff in CI context"""
        try:
            # Determine the reference to diff against
            pr_base = os.environ.get('GITHUB_BASE_REF')   # PRs
            pr_head = os.environ.get('GITHUB_HEAD_HEAD')   # PRs  
            branch = os.environ.get('GITHUB_REF_NAME')    # pushes
            
            # For maintenance branches (cherry-picks) or main branch pushes, use single commit diff
            if (branch and branch.endswith('.x')) or (branch == 'main'):
                # Maintenance branch or main branch - use diff with previous commit
                cmd = ["git", "diff", "--name-only", "HEAD~1", "HEAD"]
            elif base_ref:
                # Explicit base reference provided - use two-dot diff for direct comparison
                cmd = ["git", "diff", "--name-only", f"{base_ref}..HEAD"]
            elif pr_base and pr_head:
                # PR context - compare against base branch
                cmd = ["git", "diff", "--name-only", f"origin/{pr_base}..HEAD"]
            elif pr_base:
                # PR context fallback
                cmd = ["git", "diff", "--name-only", f"origin/{pr_base}..HEAD"]
            else:
                # Final fallback - single commit diff (most reliable)
                cmd = ["git", "diff", "--name-only", "HEAD~1..HEAD"]
            
            # Execute the git diff command
            self._last_git_command = ' '.join(cmd)  # Store for debugging
            result = subprocess.run(
                cmd, 
                cwd=self.repo_root,
                capture_output=True, 
                text=True, 
                check=True
            )
            
            files = result.stdout.strip().split('\n')
            return [f for f in files if f.strip()]
            
        except subprocess.CalledProcessError as e:
            print(f"Git command failed: {e}", file=sys.stderr)
            print(f"Command: {' '.join(e.cmd) if hasattr(e, 'cmd') else 'unknown'}", file=sys.stderr)
            print(f"Exit code: {e.returncode}", file=sys.stderr)
            print(f"Stdout: {e.stdout if hasattr(e, 'stdout') else 'N/A'}", file=sys.stderr)
            print(f"Stderr: {e.stderr if hasattr(e, 'stderr') else 'N/A'}", file=sys.stderr)
            return []
        except Exception as e:
            print(f"Error getting changed files: {e}", file=sys.stderr)
            print(f"Error type: {type(e).__name__}", file=sys.stderr)
            import traceback
            print(f"Traceback: {traceback.format_exc()}", file=sys.stderr)
            return []
    
    def _discover_affected_modules(self, changed_files: List[str]) -> List[str]:
        """Identify which Maven modules are affected by the changed files"""
        modules = set()
        
        for file_path in changed_files:
            module = self._find_module_for_file(file_path)
            # DEBUG: Print what we're finding
            print(f"DEBUG: file={file_path} -> module={module}", file=sys.stderr)
            if module and module != ".":  # Exclude root module to prevent full builds
                modules.add(module)
                print(f"DEBUG: Added module: {module}", file=sys.stderr)
            elif module == ".":
                print(f"DEBUG: Excluded root module for file: {file_path}", file=sys.stderr)
        
        print(f"DEBUG: Final modules before return: {sorted(list(modules))}", file=sys.stderr)
        return sorted(list(modules))
    
    def _find_module_for_file(self, file_path: str) -> Optional[str]:
        """Find the Maven module that contains a given file"""
        # Skip non-relevant files
        if not self._is_relevant_file(file_path):
            return None
        
        # Walk up the path looking for pom.xml
        path_parts = file_path.split('/')
        
        for i in range(len(path_parts), 0, -1):
            potential_module = '/'.join(path_parts[:i])
            # Handle root case - empty string becomes "."
            if not potential_module:
                potential_module = "."
            pom_path = self.repo_root / potential_module / "pom.xml"
            
            if pom_path.exists():
                # Never return root module to prevent full builds
                if potential_module == ".":
                    return None
                # Found a module - return the relative path from repo root
                return potential_module
        
        # Never return root module to prevent full builds
        return None
    
    def _is_relevant_file(self, file_path: str) -> bool:
        """Check if a file is relevant for module discovery"""
        # Always exclude root pom.xml to prevent full builds
        if file_path == 'pom.xml':
            return False
            
        # Include Java source and test files
        if file_path.endswith('.java'):
            return True
        
        # Include build files (but not root pom.xml - handled above)
        if file_path.endswith('pom.xml'):
            return True
        
        # Include resource files
        if '/src/main/resources/' in file_path or '/src/test/resources/' in file_path:
            return True
        
        # Include Spring Boot configuration files
        if file_path.endswith('.yml') or file_path.endswith('.yaml'):
            if '/src/main/resources/' in file_path or '/src/test/resources/' in file_path:
                return True
        
        # Include properties files in resources
        if file_path.endswith('.properties'):
            if '/src/main/resources/' in file_path or '/src/test/resources/' in file_path:
                return True
        
        # Skip documentation, root configs, etc.
        if file_path.endswith('.md') or file_path.endswith('.adoc'):
            return False
        
        if file_path in ['README.md', 'LICENSE.txt', 'CONTRIBUTING.adoc']:
            return False
            
        return False
    
    def _detect_default_base(self) -> str:
        """Detect the default base reference for verbose logging"""
        pr_base = os.environ.get('GITHUB_BASE_REF')
        branch = os.environ.get('GITHUB_REF_NAME')
        
        # Show the actual strategy being used
        if (branch and branch.endswith('.x')) or (branch == 'main'):
            branch_type = "maintenance" if branch.endswith('.x') else "main"
            return f"git diff HEAD~1 HEAD ({branch_type} branch {branch})"
        elif pr_base:
            return f"origin/{pr_base} (PR base)"
        elif branch:
            return f"HEAD~1 (single commit - branch {branch})"
        else:
            return "HEAD~1 (single commit - fallback)"
    
    def _get_last_git_command(self) -> str:
        """Get the last git command executed for debugging"""
        return self._last_git_command or "No git command executed yet"


def modules_from_diff_cli():
    """Get affected modules from git diff (for GitHub Actions)"""
    base = None
    verbose = False
    
    # Parse command line arguments
    args = sys.argv[2:]  # Skip script name and command
    i = 0
    while i < len(args):
        if args[i] == "--base" and i + 1 < len(args):
            base = args[i + 1]
            i += 2
        elif args[i] == "--verbose":
            verbose = True
            i += 1
        else:
            print(f"Unknown argument: {args[i]}", file=sys.stderr)
            i += 1
    
    discovery = CITestDiscovery()
    result = discovery.modules_from_diff(base_ref=base, verbose=verbose)
    print(result)  # Print to stdout for GitHub Actions to capture


def main():
    """CLI entry point"""
    if len(sys.argv) < 2:
        print("Usage: test_discovery.py <command> [options]", file=sys.stderr)
        print("Commands:", file=sys.stderr)
        print("  modules-from-diff [--base <ref>] [--verbose]  - Output comma-separated list of affected Maven modules", file=sys.stderr)
        print("", file=sys.stderr)
        print("Options:", file=sys.stderr)
        print("  --base <ref>   - Explicit base reference for git diff (e.g., origin/1.0.x)", file=sys.stderr)
        print("  --verbose      - Show detailed logging to stderr", file=sys.stderr)
        sys.exit(1)
    
    command = sys.argv[1]
    
    if command == "modules-from-diff":
        modules_from_diff_cli()
    else:
        print(f"Unknown command: {command}", file=sys.stderr)
        print("Available commands: modules-from-diff", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()