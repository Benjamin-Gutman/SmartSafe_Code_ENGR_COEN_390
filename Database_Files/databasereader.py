
import sqlite3
import sys
import os

def main():
    if len(sys.argv) < 2:
        print("Usage: python sqlite_reader.py <database.db>")
        sys.exit(1)

    db_file = sys.argv[1]

    if not os.path.isfile(db_file):
        print(f"[ERROR] Database file not found: {db_file}")
        sys.exit(1)

    try:
        conn = sqlite3.connect(f"file:{os.path.abspath(db_file)}?mode=ro", uri=True)
        cursor = conn.cursor()

        print(f"\n[+] Connected to: {db_file}")

        while True:
            cursor.execute("""
                SELECT name FROM sqlite_master
                WHERE type = 'table'
                ORDER BY name
            """)
            tables = [row[0] for row in cursor.fetchall()]

            print("\n========== SQLITE DATABASE READER ==========")
            print(f"Database: {db_file}")
            print(f"Tables: {len(tables)}")
            print("--------------------------------------------")

            for i, table in enumerate(tables, 1):
                print(f" [{i}] {table}")

            print("--------------------------------------------")
            print(" [0] Exit")

            choice = input("\nSelect a table: ").strip()

            if choice == "0":
                break

            if not choice.isdigit() or not (1 <= int(choice) <= len(tables)):
                print("[!] Invalid selection.")
                continue

            table = tables[int(choice) - 1]

            # Display table structure
            cursor.execute(f'PRAGMA table_info("{table}")')
            columns = cursor.fetchall()

            print(f"\n========== TABLE: {table} ==========")
            print("\nColumns:")

            for col in columns:
                print(f"  {col[1]} ({col[2]})")

            # Display records
            cursor.execute(f'SELECT * FROM "{table}"')
            rows = cursor.fetchall()

            print(f"\nTotal records: {len(rows)}")
            print("--------------------------------------------")

            if not rows:
                print("[!] This table is empty.")
            else:
                column_names = [col[1] for col in columns]

                for index, row in enumerate(rows, 1):
                    print(f"\nRecord #{index}")

                    for name, value in zip(column_names, row):
                        print(f"  {name}: {value}")

            input("\nPress ENTER to return to menu...")

        conn.close()
        print("\n[+] Database closed.")

    except sqlite3.Error as e:
        print(f"\n[ERROR] SQLite: {e}")


if __name__ == "__main__":
    main()
